"""Unit tests for the pure parts of extract-vehicles.py.

Run: python -I -m unittest discover -s tools -p "test_*.py"
"""
import importlib.util
import pathlib
import sys
import unittest

_HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(_HERE))
from dexlib import Insn  # noqa: E402

_spec = importlib.util.spec_from_file_location("extract_vehicles", _HERE / "extract-vehicles.py")
ev = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ev)

SEND_INT = "Lcom/syu/remote/RemoteTools;.sendInt"

# Car_0374_PA_Jeep_Wrangler.sendCmd(int), transcribed from its disassembly:
# mTools.sendInt(7, 6, new int[]{1, cmd}). registers_size 8, so the argument is v7.
WK2_SENDCMD_INSNS = [
    Insn(0x12, (5,), 1),
    Insn(0x54, (0, 6), None, "Lcom/syu/air/console/Air;.mTools"),
    Insn(0x38),                                   # if-eqz mTools
    Insn(0x54, (0, 6), None, "Lcom/syu/air/console/Air;.mTools"),
    Insn(0x12, (1,), 7),
    Insn(0x12, (2,), 6),
    Insn(0x12, (3,), 2),
    Insn(0x23, (3, 3)),
    Insn(0x12, (4,), 0),
    Insn(0x4b, (5, 3, 4)),                        # arr[0] = 1
    Insn(0x4b, (7, 3, 5)),                        # arr[1] = cmd
    Insn(0x6e, (0, 1, 2, 3), None, SEND_INT),
    Insn(0x0e),
]

# Car_0020_XP_KMR.sendCmd(int): sendInt(7, 25, {cmd, 1}), then a delayed release.
KMR_SENDCMD_INSNS = [
    Insn(0x12, (5,), 1),
    Insn(0x54, (0, 6), None, "Lcom/syu/air/console/Air;.mTools"),
    Insn(0x38),
    Insn(0x54, (0, 6), None, "Lcom/syu/air/console/Air;.mTools"),
    Insn(0x12, (1,), 7),
    Insn(0x13, (2,), 25),
    Insn(0x12, (3,), 2),
    Insn(0x23, (3, 3)),
    Insn(0x12, (4,), 0),
    Insn(0x4b, (7, 3, 4)),                        # arr[0] = cmd
    Insn(0x4b, (5, 3, 5)),                        # arr[1] = 1
    Insn(0x6e, (0, 1, 2, 3), None, SEND_INT),
    Insn(0x54, (0, 6), None, "Lcom/syu/air/console/Air;.mHandler"),
    Insn(0x6e, (0, 1, 2, 3), None, "Landroid/os/Handler;.postDelayed"),
    Insn(0x0e),
]


class ExtractVehiclesTest(unittest.TestCase):

    def test_table_from_record_maps_synonyms_and_method_literals(self):
        rec = {"iput": [["<init>", "C_SEAT_HEAT_LEFT", 13]], "sendcmd": [["airAc", 1], ["airTempLeftP", 4]]}
        self.assertEqual(ev.table_from_record(rec), {"SEAT_HEAT_L": 13, "AC": 1, "TEMP_L_UP": 4})

    def test_a_constant_zeroed_then_set_keeps_its_last_nonzero_value(self):
        # Some profiles clear every C_ field to 0 in <init> and assign the real
        # index later. jadx drops the zeroing, so the old extraction never saw it.
        rec = {"iput": [["<init>", "C_AC", 0], ["initCmd", "C_AC", 16], ["<init>", "C_AUTO", 0]], "sendcmd": []}
        self.assertEqual(ev.table_from_record(rec), {"AC": 16})

    def test_supported_needs_driver_temps_and_ac_or_fan(self):
        self.assertTrue(ev.supported({"TEMP_L_UP": 4, "TEMP_L_DOWN": 5, "AC": 1}))
        self.assertTrue(ev.supported({"TEMP_L_UP": 4, "TEMP_L_DOWN": 5, "FAN_UP": 6, "FAN_DOWN": 7}))
        self.assertFalse(ev.supported({"TEMP_L_UP": 4, "TEMP_L_DOWN": 5, "FAN_UP": 6}))
        self.assertFalse(ev.supported({"AC": 1, "AUTO": 2}))

    def test_duplicate_indices_are_rejected(self):
        self.assertTrue(ev.has_duplicates({"AC": 1, "AUTO": 1}))
        self.assertFalse(ev.has_duplicates({"AC": 1, "AUTO": 2}))

    def test_wk2_send_shape_is_recognised(self):
        self.assertEqual(ev.classify_send(WK2_SENDCMD_INSNS, arg_reg=7), "7/6/{1,cmd}")

    def test_press_release_shape_is_not(self):
        self.assertNotEqual(ev.classify_send(KMR_SENDCMD_INSNS, arg_reg=7), "7/6/{1,cmd}")

    def test_the_wk2_shape_with_a_delayed_post_is_not(self):
        posted = WK2_SENDCMD_INSNS[:-1] + [
            Insn(0x6e, (0, 1, 2, 3), None, "Landroid/os/Handler;.postDelayed"), Insn(0x0e)]
        self.assertNotEqual(ev.classify_send(posted, arg_reg=7), "7/6/{1,cmd}")

    def test_tsv_round_trip_lines(self):
        self.assertEqual(
            ev.render_v(2687350, "Car_0374_PA_Jeep_Wrangler", "CAR_PA_GrandCherokee_14_22", {"MAX_AC": 15}),
            "V\t2687350\tCar_0374_PA_Jeep_Wrangler\tCAR_PA_GrandCherokee_14_22\tMAX_AC=15")
        self.assertEqual(ev.render_v(374, "Car_0374_PA_Jeep_All", None, {}),
                         "V\t374\tCar_0374_PA_Jeep_All\t-\t-")
        self.assertEqual(ev.render_p("X", {"AUTO": 2, "AC": 1}), "P\tX\tAC=1,AUTO=2")

    def _wk2_with(self, at, *insns):
        """The WK2 body with instructions inserted before index [at]."""
        return WK2_SENDCMD_INSNS[:at] + list(insns) + WK2_SENDCMD_INSNS[at:]

    def test_a_modified_argument_is_not_the_trusted_frame(self):
        # add-int/lit8 v7, v7, 16 before the array is filled: sends a different index.
        self.assertNotEqual(ev.classify_send(self._wk2_with(10, Insn(0xd8, (7, 7), 16)), arg_reg=7), "7/6/{1,cmd}")

    def test_any_other_invoke_is_not_the_trusted_frame(self):
        release = Insn(0x6e, (6, 7), None, "Lcom/syu/air/canbus/Car_X;.sendRelease")
        self.assertNotEqual(ev.classify_send(self._wk2_with(12, release), arg_reg=7), "7/6/{1,cmd}")

    def test_a_three_element_array_is_not_the_trusted_frame(self):
        insns = list(WK2_SENDCMD_INSNS)
        insns[6] = Insn(0x12, (3,), 3)          # new int[3]
        self.assertNotEqual(ev.classify_send(insns, arg_reg=7), "7/6/{1,cmd}")

    def test_a_constant_overwritten_by_a_field_read_is_not_trusted(self):
        # v2 = 6, then v2 = this.CMD_TYPE: the 6 no longer holds at the call.
        overwrite = Insn(0x52, (2, 6), None, "Lcom/syu/air/console/Air;.CMD_TYPE")
        self.assertNotEqual(ev.classify_send(self._wk2_with(6, overwrite), arg_reg=7), "7/6/{1,cmd}")


if __name__ == "__main__":
    unittest.main()
