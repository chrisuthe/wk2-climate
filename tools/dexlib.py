"""Read-only DEX reading, standard library only.

Enough of the format to pull per-vehicle command tables out of an APK without
a decompiler: string, type, field and method pools; each class's static int
values; `const` -> `iput`/`sput` assignments; literal `sendCmd(n)` calls; and a
crude instruction decoder for inspecting one method's body.

It is not a disassembler. Only the opcodes the extractor reasons about are
decoded into operands; everything else is carried as a bare opcode with the
right width so the walk stays aligned.
"""
import struct
import zipfile
from collections import namedtuple

Insn = namedtuple("Insn", "op regs const ref")
Insn.__new__.__defaults__ = ((), None, None)


def uleb(b, i):
    r = s = 0
    while True:
        x = b[i]
        i += 1
        r |= (x & 0x7f) << s
        s += 7
        if x < 0x80:
            return r, i


# Instruction width in 16-bit code units, by opcode.
W = [1] * 256
for _op, _w in [(0x02, 2), (0x03, 3), (0x05, 2), (0x06, 3), (0x08, 2), (0x09, 3),
                (0x13, 2), (0x14, 3), (0x15, 2), (0x16, 2), (0x17, 3), (0x18, 5),
                (0x19, 2), (0x1a, 2), (0x1b, 3), (0x1c, 2), (0x1f, 2), (0x20, 2),
                (0x22, 2), (0x23, 2), (0x24, 3), (0x25, 3), (0x26, 3), (0x29, 2),
                (0x2a, 3), (0x2b, 3), (0x2c, 3), (0xfa, 4), (0xfb, 4), (0xfc, 3),
                (0xfd, 3), (0xfe, 2), (0xff, 2)]:
    W[_op] = _w
for _lo, _hi, _w in [(0x2d, 0x3d, 2), (0x44, 0x6d, 2), (0x6e, 0x72, 3), (0x74, 0x78, 3),
                     (0x90, 0xaf, 2), (0xd0, 0xe2, 2)]:
    for _op in range(_lo, _hi + 1):
        W[_op] = _w


class Dex:
    def __init__(self, b):
        self.b = b
        u = lambda o: struct.unpack_from('<I', b, o)[0]
        ns, so = u(0x38), u(0x3c)
        self.strs = []
        for k in range(ns):
            p = u(so + 4 * k)
            _, p = uleb(b, p)
            self.strs.append(b[p:b.index(b'\0', p)].decode('utf-8', 'replace'))
        nt, to = u(0x40), u(0x44)
        self.types = [self.strs[u(to + 4 * k)] for k in range(nt)]
        nf, fo = u(0x50), u(0x54)
        self.fields = []
        for k in range(nf):
            c, _, n = struct.unpack_from('<HHI', b, fo + 8 * k)
            self.fields.append((self.types[c], self.strs[n]))
        nm, mo = u(0x58), u(0x5c)
        self.methods = []
        for k in range(nm):
            c, _, n = struct.unpack_from('<HHI', b, mo + 8 * k)
            self.methods.append((self.types[c], self.strs[n]))
        self.ncls, self.co = u(0x60), u(0x64)

    def enc_value(self, i):
        b = self.b
        h = b[i]
        i += 1
        t, a = h & 0x1f, h >> 5
        if t in (0x1e, 0x1f):
            return (True if (t == 0x1f and a) else (False if t == 0x1f else None)), i
        if t == 0x1c:
            n, i = uleb(b, i)
            out = []
            for _ in range(n):
                v, i = self.enc_value(i)
                out.append(v)
            return out, i
        if t == 0x1d:
            _, i = uleb(b, i)
            n, i = uleb(b, i)
            for _ in range(n):
                _, i = uleb(b, i)
                _, i = self.enc_value(i)
            return None, i
        raw = b[i:i + a + 1]
        i += a + 1
        if t in (0x00, 0x02, 0x04, 0x06):
            return int.from_bytes(raw, 'little', signed=True), i
        if t == 0x03:
            return int.from_bytes(raw, 'little'), i
        if t == 0x17:
            return self.strs[int.from_bytes(raw, 'little')], i
        return None, i

    def _class_methods(self, cdo):
        """Yield (method name, code offset) for every direct and virtual method."""
        b = self.b
        i = cdo
        sf, i = uleb(b, i)
        inf, i = uleb(b, i)
        dm, i = uleb(b, i)
        vm, i = uleb(b, i)
        for _ in range(sf + inf):
            _, i = uleb(b, i)
            _, i = uleb(b, i)
        for cnt in (dm, vm):
            idx = 0  # method indices are delta-encoded per list, not across both
            for _ in range(cnt):
                d, i = uleb(b, i)
                _, i = uleb(b, i)
                code, i = uleb(b, i)
                idx += d
                yield self.methods[idx][1], code

    def _class_static_fields(self, cdo):
        b = self.b
        i = cdo
        sf, i = uleb(b, i)
        _, i = uleb(b, i)
        _, i = uleb(b, i)
        _, i = uleb(b, i)
        names, idx = [], 0
        for _ in range(sf):
            d, i = uleb(b, i)
            _, i = uleb(b, i)
            idx += d
            names.append(self.fields[idx][1])
        return names

    def class_defs(self):
        """Yield (class descriptor, class_data offset, static values offset)."""
        for k in range(self.ncls):
            o = self.co + 32 * k
            ci, _, _, _, _, _, cdo, svo = struct.unpack_from('<IIIIIIII', self.b, o)
            yield self.types[ci], cdo, svo

    def records(self):
        """Per class: static int values, const->iput/sput, literal sendCmd(n), method names."""
        for name, cdo, svo in self.class_defs():
            rec = {'static': {}, 'iput': [], 'sendcmd': [], 'methods': []}
            if cdo:
                if svo:
                    n, j = uleb(self.b, svo)
                    for f in self._class_static_fields(cdo)[:n]:
                        v, j = self.enc_value(j)
                        if isinstance(v, (int, str)) and not isinstance(v, bool):
                            rec['static'][f] = v
                for mname, code in self._class_methods(cdo):
                    rec['methods'].append(mname)
                    if code:
                        self._walk(code, mname, rec)
            yield name, rec

    def _walk(self, co, mname, rec):
        reg = {}
        for ins in self.decode(co):
            if ins.op in (0x12, 0x13, 0x14):
                reg[ins.regs[0]] = ins.const
            elif 0x59 <= ins.op <= 0x5f and ins.regs[0] in reg:      # iput*
                rec['iput'].append([mname, ins.ref.split('.')[-1], reg[ins.regs[0]]])
            elif 0x67 <= ins.op <= 0x6d and ins.regs[0] in reg:      # sput*
                rec['iput'].append([mname, ins.ref, reg[ins.regs[0]]])
            elif ins.op in (0x6e, 0x70) and ins.ref.endswith('.sendCmd') and len(ins.regs) >= 2:
                if ins.regs[1] in reg:
                    rec['sendcmd'].append([mname, reg[ins.regs[1]]])

    def registers(self, co):
        return struct.unpack_from('<H', self.b, co)[0]

    def decode(self, co):
        """Decode one method body into Insn tuples. Stops at the first payload table."""
        b = self.b
        n, = struct.unpack_from('<I', b, co + 12)
        p, end = co + 16, co + 16 + 2 * n
        out = []
        while p < end:
            u0 = struct.unpack_from('<H', b, p)[0]
            op = u0 & 0xff
            if op == 0x00 and u0 in (0x0100, 0x0200, 0x0300):
                break
            if op == 0x12:
                v = u0 >> 12
                out.append(Insn(op, ((u0 >> 8) & 0xf,), v - 16 if v > 7 else v))
            elif op == 0x13:
                out.append(Insn(op, (u0 >> 8,), struct.unpack_from('<h', b, p + 2)[0]))
            elif op == 0x14:
                out.append(Insn(op, (u0 >> 8,), struct.unpack_from('<i', b, p + 2)[0]))
            elif 0x52 <= op <= 0x5f:                                  # iget*/iput*
                f = self.fields[struct.unpack_from('<H', b, p + 2)[0]]
                out.append(Insn(op, ((u0 >> 8) & 0xf, u0 >> 12), None, f[0] + '.' + f[1]))
            elif 0x60 <= op <= 0x6d:                                  # sget*/sput*
                f = self.fields[struct.unpack_from('<H', b, p + 2)[0]]
                out.append(Insn(op, (u0 >> 8,), None, f[0] + '.' + f[1]))
            elif op == 0x23:                                          # new-array
                out.append(Insn(op, ((u0 >> 8) & 0xf, u0 >> 12)))
            elif 0x4b <= op <= 0x51:                                  # aput*: value, array, index
                out.append(Insn(op, (u0 >> 8, b[p + 2], b[p + 3])))
            elif 0x6e <= op <= 0x72:                                  # invoke-*
                m = self.methods[struct.unpack_from('<H', b, p + 2)[0]]
                cnt = u0 >> 12
                rr = struct.unpack_from('<H', b, p + 4)[0]
                regs = (rr & 0xf, (rr >> 4) & 0xf, (rr >> 8) & 0xf, (rr >> 12) & 0xf, (u0 >> 8) & 0xf)[:cnt]
                out.append(Insn(op, regs, None, m[0] + '.' + m[1]))
            elif 0x74 <= op <= 0x78:                                  # invoke-*/range
                m = self.methods[struct.unpack_from('<H', b, p + 2)[0]]
                first = struct.unpack_from('<H', b, p + 4)[0]
                out.append(Insn(op, tuple(range(first, first + (u0 >> 8))), None, m[0] + '.' + m[1]))
            else:
                out.append(Insn(op))
            p += 2 * W[op]
        return out


def load_apk(path):
    """Every classes*.dex in the APK, in name order."""
    z = zipfile.ZipFile(path)
    return [Dex(z.read(nm)) for nm in sorted(z.namelist())
            if nm.startswith('classes') and nm.endswith('.dex')]


def method_code(dexes, class_desc, method_name):
    """(registers_size, [Insn]) for the named method, or None if it has no body."""
    for d in dexes:
        for name, cdo, _ in d.class_defs():
            if name != class_desc or not cdo:
                continue
            for mname, code in d._class_methods(cdo):
                if mname == method_name and code:
                    return d.registers(code), d.decode(code)
    return None
