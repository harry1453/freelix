package pocxor

import burst.kit.crypto.BurstCrypto
import java.util.function.Supplier

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val burstCrypto = BurstCrypto.getInstance()
        if (args.size < 2) {
            println("Usage:\nplot id\nget id scoop")
            return
        }

        val id = java.lang.Long.parseUnsignedLong(args[1])

        when {
            args[0].compareTo("plot", ignoreCase = true) == 0 -> {
                val xorPlotter = XorPlotter(id)
                xorPlotter.plot()
            }
            args[0].compareTo("get", ignoreCase = true) == 0 -> {
                val scoop = if (args.size < 3) 0 else Integer.parseInt(args[2])
                val xorGetter = XorGetter(id, scoop)
                val results = xorGetter.get()

                for (nonce in results.indices) {
                    println("n" + nonce + "s" + scoop + " " + burstCrypto.toHexString(results[nonce]))
                }
            }
            args[0].compareTo("test", ignoreCase = true) == 0 -> { // just for checking results easily
                val nonce = if (args.size < 3) 0 else java.lang.Long.parseLong(args[2])
                val scoop = if (args.size < 4) 0 else Integer.parseInt(args[3])
                val miningPlot = MiningPlot(Supplier { burstCrypto.shabal256 }, id, nonce, 2)
                println("n" + nonce + "s" + scoop + " " + burstCrypto.toHexString(miningPlot.getScoop(scoop)))
            }
            else -> println("Invalid op")
        }
    }
}