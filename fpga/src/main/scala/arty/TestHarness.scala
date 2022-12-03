package chipyard.fpga.arty

import chisel3._

import freechips.rocketchip.diplomacy.{LazyModule}
import freechips.rocketchip.config.{Parameters}

import sifive.fpgashells.shell.xilinx.artyshell.{ArtyShell}

import chipyard.{BuildTop, HasHarnessSignalReferences}
import chipyard.harness.{ApplyHarnessBinders}
import chipyard.iobinders.{HasIOBinders}
import testchipip.ClockDivider

class ArtyFPGATestHarness(override implicit val p: Parameters) extends ArtyShell with HasHarnessSignalReferences {

  val lazyDut = LazyModule(p(BuildTop)(p)).suggestName("chiptop")

  // Convert harness resets from Bool to Reset type.
  val hReset = Wire(Reset())
  hReset := ~ck_rst

  val dReset = Wire(AsyncReset())
  dReset := reset_core.asAsyncReset

  val slowClock = withClockAndReset(clock_65MHz, hReset) {
    val divider = Module(new testchipip.ClockDivider(5))
    divider.io.divisor := 31.U(5.W)
    divider.io.clockOut
  }

  val buildtopClock = slowClock // hacked to be 200khz

  // default to 32MHz clock
  withClockAndReset(buildtopClock, hReset) {
    val dut = Module(lazyDut.module)
  }

  val buildtopReset = hReset
  val success = false.B

  val dutReset = dReset

  // must be after HasHarnessSignalReferences assignments
  lazyDut match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }
}

