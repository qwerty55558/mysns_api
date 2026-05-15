package com.mysns.main.ocr

import org.springframework.stereotype.Component

@Component
class AmountExtractor {

    /**
     * 영수증 텍스트에서 결제 금액 추출.
     * - 우선순위 1: 합계/총계/결제 키워드 라인의 가장 큰 숫자
     * - 우선순위 2: 전체 텍스트의 가장 큰 숫자 (≥100)
     * 한 줄에 여러 숫자가 있으면 최댓값. 100원 미만은 영수증 잡음으로 간주하고 제외.
     */
    fun extract(rawText: String): Int? {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        for (kw in KEYWORDS) {
            val line = lines.firstOrNull { it.contains(kw, ignoreCase = true) } ?: continue
            val amount = numbersIn(line).filter { it >= MIN_AMOUNT }.maxOrNull()
            if (amount != null) return amount
        }

        return lines.flatMap { numbersIn(it) }.filter { it >= MIN_AMOUNT }.maxOrNull()
    }

    private fun numbersIn(line: String): List<Int> =
        NUMBER_RE.findAll(line)
            .map { it.value.replace(",", "").toIntOrNull() }
            .filterNotNull()
            .toList()

    companion object {
        private val NUMBER_RE = """[\d,]{2,}""".toRegex()
        private const val MIN_AMOUNT = 100
        private val KEYWORDS = listOf(
            "합계", "합 계", "총계", "총 계", "총액", "총 액",
            "결제금액", "결제 금액", "결제액", "받을금액", "받을 금액",
            "TOTAL", "AMOUNT", "PAID",
        )
    }
}
