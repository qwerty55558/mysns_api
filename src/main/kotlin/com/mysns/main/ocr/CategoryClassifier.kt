package com.mysns.main.ocr

import com.mysns.main.graphql.model.PostCategory
import org.springframework.stereotype.Component

@Component
class CategoryClassifier {

    /**
     * 영수증 텍스트 → PostCategory enum.
     * 키워드 사전 기반. 가장 먼저 매칭되는 카테고리 반환.
     * 추후 정확도 부족 시 LLM/임베딩으로 교체.
     */
    fun classify(rawText: String): PostCategory? {
        val upper = rawText.uppercase()
        for ((category, kws) in KEYWORDS_BY_CATEGORY) {
            if (kws.any { upper.contains(it.uppercase()) }) return category
        }
        return null
    }

    companion object {
        private val KEYWORDS_BY_CATEGORY: Map<PostCategory, List<String>> = mapOf(
            PostCategory.CAFE to listOf(
                "스타벅스", "스벅", "투썸", "이디야", "할리스", "메가커피", "빽다방",
                "공차", "탐앤탐스", "폴바셋", "커피빈",
                "STARBUCKS", "COFFEE", "CAFE", "TWOSOME", "EDIYA",
            ),
            PostCategory.GROCERY to listOf(
                "GS25", "CU", "세븐일레븐", "이마트24", "이마트", "홈플러스", "롯데마트",
                "노브랜드", "코스트코", "농협하나로", "농협 하나로", "마트", "편의점",
                "EMART", "COSTCO", "HOMEPLUS",
            ),
            PostCategory.FOOD to listOf(
                "맥도날드", "롯데리아", "버거킹", "KFC", "맘스터치", "서브웨이",
                "BHC", "BBQ", "굽네", "교촌", "네네", "치킨",
                "백반", "식당", "분식", "김밥", "떡볶이", "라멘", "라면", "냉면",
                "정식", "한정식", "고깃집", "삼겹살", "보쌈", "족발", "곱창",
                "MCDONALDS", "BURGERKING", "SUBWAY",
            ),
            PostCategory.TRANSPORT to listOf(
                "지하철", "교통카드", "버스", "택시", "TAXI", "카카오T", "카카오 T",
                "쏘카", "그린카", "타다", "KTX", "SRT", "코레일",
            ),
            PostCategory.SHOPPING to listOf(
                "유니클로", "ZARA", "H&M", "백화점", "아울렛",
                "쿠팡", "GMARKET", "G마켓", "11번가", "위메프", "티몬",
                "올리브영", "다이소", "무신사", "29CM",
                "UNIQLO", "OUTLET", "COUPANG",
            ),
            PostCategory.ENTERTAINMENT to listOf(
                "CGV", "롯데시네마", "메가박스", "영화관", "영화",
                "PC방", "노래방", "볼링장", "당구장",
                "넷플릭스", "왓챠", "디즈니",
                "MEGABOX", "LOTTE CINEMA",
            ),
            PostCategory.BILLS to listOf(
                "전기요금", "한국전력", "한전", "수도요금", "가스요금",
                "통신요금", "SKT", "KT", "LGU+", "LG U+",
                "관리비", "공과금", "월세", "전세",
            ),
            PostCategory.HEALTH to listOf(
                "약국", "병원", "의원", "한의원", "치과", "안과", "피부과",
                "처방전", "처방", "PHARMACY", "CLINIC", "HOSPITAL",
            ),
            PostCategory.TRAVEL to listOf(
                "호텔", "에어비앤비", "AIRBNB", "여행", "관광",
                "항공", "대한항공", "아시아나", "제주항공", "진에어",
                "HOTEL", "AIRLINE",
            ),
        )
    }
}
