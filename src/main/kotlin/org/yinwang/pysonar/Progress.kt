package org.yinwang.pysonar

private const val MAX_SPEED_DIGITS = 5

class Progress(private var total: Long, private var width: Long) {
	private var startTime: Long = 0
	private var lastTickTime: Long = 0
	private var lastCount: Long = 0
	private var lastRate: Int = 0
	private var lastAvgRate: Int = 0
	private var count: Long = 0
	private var segSize: Long = 0

	init {
		this.startTime = System.currentTimeMillis()
		this.lastTickTime = System.currentTimeMillis()
		this.lastCount = 0
		this.lastRate = 0
		this.lastAvgRate = 0
		this.segSize = total / width
		if (segSize == 0L) segSize = 1
	}

	private fun tick(n: Int) {
		count += n.toLong()
		if (count > total) {
			total = count
		}

		val elapsed = System.currentTimeMillis() - lastTickTime

		if (elapsed > 500 || count == total) {
			msg("\r")
			val dlen = Math.ceil(Math.log10(total.toDouble())).toInt()
			msg(percent(count, total) + " (" +
					formatNumber(count, dlen) +
					" of " + formatNumber(total, dlen) + ")")

			val rate: Int = if (elapsed > 1) ((count - lastCount) / (elapsed / 1000.0)).toInt() else lastRate

			lastRate = rate
			msg("   SPEED: " + formatNumber(rate, MAX_SPEED_DIGITS) + "/s")

			val totalElapsed = System.currentTimeMillis() - startTime
			var avgRate = if (totalElapsed > 1) (count / (totalElapsed / 1000.0)).toInt() else lastAvgRate

			avgRate = if (avgRate == 0) 1 else avgRate

			msg("   AVG SPEED: " + formatNumber(avgRate, MAX_SPEED_DIGITS) + "/s")

			val remain = total - count
			val remainTime = remain / avgRate * 1000
			msg("   ETA: " + formatTime(remainTime))
			msg("   PARSE ERRS: " + Analyzer.self.failedToParse.size)
			msg("       ")      // overflow area

			lastTickTime = System.currentTimeMillis()
			lastAvgRate = avgRate
			lastCount = count
		}
	}


	fun tick() {
		if (!Analyzer.self.hasOption("quiet")) tick(1)
	}
}
