// See LICENSE for license details.

package peri.mem

import chisel3.iotesters._
import dirv.MemCmd
import dirv.io.{MemIO, MemResp}
import test.util.BaseTester

/**
  * Unit test class for MbusSramBridge
  * @param c Instance of SimDTMMbusSramBridge
  */
class MemTopUnitTester(c: SimDTMMemTop) extends PeekPokeTester(c) {

  val imem = c.io.dut.imem
  val dmem = c.io.dut.dmem

  def idle(cycle: Int = 1): Unit = {
    poke(imem.valid, false)
    poke(imem.r.get.ready, true)
    poke(dmem.valid, false)
    poke(dmem.w.get.valid, false)
    poke(dmem.r.get.ready, true)
    step(cycle)
  }

  /**
    * Dmem write request
    * @param addr Address to write
    */
  def d_write_req(addr: Int): Unit = {
    poke(dmem.valid, true)
    poke(dmem.addr, addr)
    poke(dmem.cmd, MemCmd.wr)
  }

  /**
    * Dmem send write data
    * @param strb Valid byte lane
    * @param data Data to write
    */
  def d_write_data(strb: Int,  data: Int): Unit = {
    poke(dmem.w.get.valid, true)
    poke(dmem.w.get.strb, strb)
    poke(dmem.w.get.data, intToUnsignedBigInt(data))
  }

  /**
    * Dmem single write
    * @param addr Address to write
    * @param strb Valid byte lane
    * @param data register address
    */
  def single_write(addr: Int, strb: Int,  data: Int, wrDataLatency: Int = 0): Unit = {
    d_write_req(addr)
    if (wrDataLatency == 0) {
      d_write_data(strb, data)
    }

    var cmd_fire = BigInt(0)
    var w_fire = BigInt(0)
    var count = 0
    while ((cmd_fire != 1) || (w_fire != 1)) {
      if (count == wrDataLatency) {
        d_write_data(strb, data)
      }

      if ((peek(dmem.valid) & peek(dmem.ready)) == 1) {
        cmd_fire = 1
      }
      if ((peek(dmem.w.get.valid) & peek(dmem.w.get.ready)) == 1) {
        w_fire = 1
      }

      println(f"(cmd_ready, w_ready) = ($cmd_fire, $w_fire)")

      step(1)

      count += 1

      if (cmd_fire == 0x1) {
        poke(dmem.valid, false)
      }
      if (w_fire == 0x1) {
        poke(dmem.w.get.valid, false)
      }
    }
    step(1)
  }

  /**
    * MemIO read request
    * @param addr Address to read
    */
  def read_req(port: MemIO, addr: Int): Unit = {
    poke(port.valid, true)
    poke(port.addr, addr)
    poke(port.cmd, MemCmd.rd)
  }

  /**
    * MemIO single read
    * @param addr Address to write
    * @param exp expect value for read register
    */
  def single_read(port: MemIO, addr: Int, exp: Int, rdDataLatency: Int = 0): Unit = {
    read_req(port, addr)

    var cmd_ready = BigInt(0)
    while (cmd_ready != 1) {
      cmd_ready = peek(port.ready)

      // This check is for Zero sram read latency.
      if (rdDataLatency == 0) {
        expect(port.r.get.valid, true)
        expect(port.r.get.data, exp)
      } else {
        expect(port.r.get.valid, false)
      }

      step(1)

      if (cmd_ready == 0x1) {
        poke(port.valid, false)
      }
    }

    if (rdDataLatency != 0) {
      var r_valid = BigInt(0)
      var count = 1
      while (r_valid != 1) {
        if (count == rdDataLatency) {
          expect(port.r.get.valid, true)
          expect(port.r.get.data, exp)
        }

        r_valid = peek(port.r.get.valid)
        step(1)
        count += 1
      }
    }
  }

  /**
    * Set imem input
    * @param valid Value to be set in imem.valid
    * @param addr Value to be set in imem.addr
    * @param cmd Value to be set in imem.cmd
    * @param ready Value to be set in imem.ready
    */
  def set_imem(valid: Boolean, addr: BigInt, cmd: Int, ready: Boolean): Unit = {
    poke(imem.valid, valid)
    poke(imem.addr, addr)
    poke(imem.cmd, cmd)
    poke(imem.r.get.ready, ready)
  }

  /**
    * Compare imem output
    * @param exp_ready Expected value of imem.ready
    * @param exp_r_valid Expected value of imem.r.get.valid
    * @param exp_r_data Expected value of imem.r.get.data
    * @param exp_r_resp Expected value of imem.r.get.resp
    */
  def comp_imem(exp_ready: Boolean, exp_r_valid: Boolean, exp_r_data: Int, exp_r_resp: Int): Unit = {
    expect(imem.ready, exp_ready)
    expect(imem.r.get.valid, exp_r_valid)
    expect(imem.r.get.data, intToUnsignedBigInt(exp_r_data))
    expect(imem.r.get.resp, exp_r_resp)
 }

  /**
    * Set dmem input
    * @param valid Value to be set in dmem.valid
    * @param addr Value to be set in dmem.addr
    * @param cmd Value to be set in dmem.cmd
    * @param ready Value to be set in dmem.ready
    * @param w_valid Value to be set in dmem.w.get.valid
    * @param w_data Value to be set in dmem.w.get.data
    */
  def set_dmem(valid: Boolean, addr: BigInt, cmd: Int, ready: Boolean,
              w_valid: Boolean, w_data: BigInt): Unit = {
    poke(dmem.valid, valid)
    poke(dmem.addr, addr)
    poke(dmem.cmd, cmd)
    poke(dmem.r.get.ready, ready)
    poke(dmem.w.get.valid, w_valid)
    poke(dmem.w.get.data, w_data)
  }

  /**
    * Compare dmem output
    * @param exp_ready Expected value of dmem.ready
    * @param exp_r_valid Expected value of dmem.r.get.valid
    * @param exp_r_data Expected value of dmem.r.get.data
    * @param exp_r_resp Expected value of dmem.r.get.resp
    * @param exp_w_ready Expected value of dmem.w.get.ready
    * @param exp_w_resp Expected value of dmem.w.get.resp
    */
  def comp_dmem(exp_ready: Boolean, exp_r_valid: Boolean, exp_r_data: Int, exp_r_resp: Int,
               exp_w_ready: Boolean,  exp_w_resp: Int
               ): Unit = {
    expect(dmem.ready, exp_ready)
    expect(dmem.r.get.valid, exp_r_valid)
    expect(dmem.r.get.data, intToUnsignedBigInt(exp_r_data))
    expect(dmem.r.get.resp, exp_r_resp)
    expect(dmem.w.get.valid, exp_w_ready)
    expect(dmem.w.get.data, exp_w_resp)
  }
}

/**
  * Test class for MbusSramBridge
  */
class MemTopTester extends BaseTester {

  val dutName = "MemTopTester"

  behavior of dutName

  val timeoutCycle = 1000
  val base_p = MemTopParams(128, 32)

  it should
    f"be able to convert Mbus write access to Sram write access. [$dutName-000]" in {

    val outDir = dutName + "-000"
    val args = getArgs(Map(
      "--top-name" -> dutName,
      "--target-dir" -> s"test_run_dir/$outDir"
    ))

    Driver.execute(args, () => new SimDTMMemTop(base_p)(timeoutCycle)) {
      c => new MemTopUnitTester(c) {
        idle(10)
        single_write(0x1, 0xf, 0x12345678)
        idle(10)
      }
    } should be (true)
  }

  it should
    f"wait for issuing Sram write, when Mbus write data doesn't come. [$dutName-001]" in {

    val outDir = dutName + "-001"
    val args = getArgs(Map(
      "--top-name" -> dutName,
      "--target-dir" -> s"test_run_dir/$outDir"
    ))

    Driver.execute(args, () => new SimDTMMemTop(base_p)(timeoutCycle)) {
      c => new MemTopUnitTester(c) {
        for (delay <- 0 until 5) {
          idle(2)
          single_write(delay, 0xf, 0x12345678, delay)
          idle(2)
        }
      }
    } should be (true)
  }

  it should
    f"be able to convert Mbus read access to Sram read access. [$dutName-002]" in {

    val outDir = dutName + "-002"
    val args = getArgs(Map(
      "--top-name" -> dutName,
      "--target-dir" -> s"test_run_dir/$outDir"
    ))

    Driver.execute(args, () => new SimDTMMemTop(base_p)(timeoutCycle)) {
      c => new MemTopUnitTester(c) {
        idle(10)
        single_write(0x1, 0xf, 0x12345678)
        single_read(dmem, 0x1, 0x12345678, 1)
        idle(10)
      }
    } should be (true)
  }

  it should
    f"keep Mbus.r.data if Mbus r.ready is Low. [$dutName-BUG-100]" in {

    val outDir = dutName + "-BUG-100"
    val args = getArgs(Map(
      "--top-name" -> dutName,
      "--target-dir" -> s"test_run_dir/$outDir"
    ))

    Driver.execute(args, () => new SimDTMMemTop(base_p)(timeoutCycle)) {
      c => new MemTopUnitTester(c) {
        idle(10)

        val wrData = Seq(
          0xf0008093, 0x00008f03,
          0xfff00e93, 0x00200193)

        // Set value
        for ((data, idx) <- wrData.zipWithIndex) {
          single_write(idx * 4, 0xf, data)
        }

        var data = wrData(0)
        set_imem(true, 0x4, MemCmd.rd, true)
        step(1)

        set_imem(true, 0x8,MemCmd.rd,  true)
        comp_imem(true, true, wrData(1), MemResp.ok)
        step(1)

        // change ready signal to LOW, so mbus read data will be kept in next cycle.
        set_imem(true, 0xc, MemCmd.rd, false)
        comp_imem(true, true, wrData(2), MemResp.ok)
        step(1)

        // This bug regeneration pattern expose that
        // mbus read data doesn't keep the value in previous cycle.
        set_imem(true, 0xc, MemCmd.rd, true)
        comp_imem(true, true, wrData(2), MemResp.ok)
        step(1)

        set_imem(true, 0x10, MemCmd.rd, true)
        comp_imem(true, true, wrData(3), MemResp.ok)
        step(1)

        idle(10)
      }
    } should be (true)
  }
}
