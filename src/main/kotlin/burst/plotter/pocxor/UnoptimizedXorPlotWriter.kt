package burst.plotter.pocxor

import burst.common.Util
import burst.common.toPairList
import burst.kit.crypto.BurstCrypto
import burst.miner.pocxor.MiningPlot
import burst.plotter.PlotWriter
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.system.exitProcess

class UnoptimizedXorPlotWriter(private val id: Long) : PlotWriter {
    private val burstCrypto = BurstCrypto.getInstance()

    override fun writePlot(id: Long, startNonce: Long, nonceCount: Long, path: String) {
        val multipleRequirement = MiningPlot.SCOOPS_PER_PLOT * 2
        require(nonceCount % multipleRequirement == 0L) { "Nonce count must be a multiple of $multipleRequirement" }
        val file = RandomAccessFile(path, "rw")
        val startNonces = mutableListOf<Long>()
        for (i in 0 until nonceCount / multipleRequirement) {
            startNonces.add(i * multipleRequirement)
        }
        plot(file, startNonces.toTypedArray()).subscribe({
            file.close()
            println("Finished!")
            exitProcess(0)
        }, { it.printStackTrace() })
    }

    fun plot(output: RandomAccessFile, startNonces: Array<Long>): Completable {
        val nextStartNonceIndex = AtomicInteger(0)
        val selectNextStartNonceLock = Any()
        val writeDiskLock = Any()
        return Single.fromCallable {
            val setSize = MiningPlot.SCOOPS_PER_PLOT * MiningPlot.PLOT_SIZE

            // Set file size
            output.setLength(startNonces.size * setSize.toLong())

            // Allocate memory
            val buffers = mutableListOf<ByteArray>()

            println("Allocating memory...")
            val maxBuffers = min(startNonces.size, Runtime.getRuntime().availableProcessors())
            while (buffers.size < maxBuffers) {
                try {
                    buffers.add(ByteArray(setSize))
                } catch (e: OutOfMemoryError) {
                    break
                }
            }
            println("Allocated ${buffers.size} buffers, which means ${buffers.size / 2} threads can run. Let's go!")

            return@fromCallable buffers.toPairList()
        }
                .subscribeOn(Schedulers.io())
                .flattenAsObservable { it }
                .flatMapCompletable { buffer ->
                    Completable.fromAction {
                        var startNonce: Long
                        while (true) { // TODO not while true...
                            // Choose a startNonce
                            synchronized(selectNextStartNonceLock) {
                                if (nextStartNonceIndex.get() >= startNonces.size) {
                                    return@fromAction
                                }
                                startNonce = startNonces[nextStartNonceIndex.getAndIncrement()]
                                nextStartNonceIndex
                            }
                            println("Calculating data for start nonce $startNonce")
                            // Calculate the plot data
                            calculatePlotData(buffer.first, buffer.second, startNonce)
                            println("Start nonce $startNonce calculation finished!")
                            // Write it to disk
                            synchronized(writeDiskLock) {
                                println("Writing start nonce $startNonce to disk...")
                                output.seek(MiningPlot.PLOT_SIZE * startNonce / 2)
                                output.write(buffer.first)
                                println("Finished writing start nonce $startNonce to disk...")
                            }
                        }

                    }.subscribeOn(Schedulers.computation()) // This is computationally intensive
                }
    }

    private fun calculatePlotData(buffer: ByteArray, secondBuffer: ByteArray, startNonce: Long) {
        fun printProgress(currentNonce: Int) {
            // Update every 1%
            if (currentNonce % (MiningPlot.SCOOPS_PER_PLOT / 50 /* scoopsPerPlot*2/100 */) == 0) {
                println("Start nonce $startNonce: ${(currentNonce.toDouble() / (MiningPlot.SCOOPS_PER_PLOT * 2).toDouble() * 100).roundToLong()}% (currentNonce: $currentNonce)")
            }
        }

        burstCrypto.plotNonces(id, startNonce, MiningPlot.SCOOPS_PER_PLOT.toLong(), 2.toByte(), buffer, 0)
        printProgress(MiningPlot.SCOOPS_PER_PLOT)

        println("starting 2nd")

        burstCrypto.plotNonces(id, MiningPlot.SCOOPS_PER_PLOT + startNonce, MiningPlot.SCOOPS_PER_PLOT.toLong(), 2.toByte(), secondBuffer, 0)
        for (nonce in 0 until MiningPlot.SCOOPS_PER_PLOT) { // second set is nonce + SCOOPS_PER_PLOT and is xored
            for (scoop in 0 until MiningPlot.SCOOPS_PER_PLOT) {
                Util.xorArray(buffer, (nonce * MiningPlot.SCOOP_SIZE + scoop * MiningPlot.PLOT_SIZE), secondBuffer, nonce * MiningPlot.PLOT_SIZE + scoop * MiningPlot.SCOOP_SIZE, MiningPlot.SCOOP_SIZE)
            }
            printProgress(nonce + MiningPlot.SCOOPS_PER_PLOT)
        }

        printProgress(2 * MiningPlot.SCOOPS_PER_PLOT)

        println("Done!!!")
    }
}