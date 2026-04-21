package com.enigma.bigcharts.core.utils

import androidx.compose.ui.graphics.Color

// ── Metric card ───────────────────────────────────────────────────────────────

data class MetricCardData(
    val label: String,
    val value: String,
    val sub: String,
    val subPositive: Boolean? = null   // true=green, false=red, null=neutral
)

// ── Pie / Donut segment ───────────────────────────────────────────────────────

data class PieSegment(
    val label: String,
    val value: Float,           // percentage 0‥100
    val color: Color,
    val details: PieSegmentDetails
)

data class PieSegmentDetails(
    // Revenue pie
    val revenue: String? = null,
    val growth: String? = null,
    val customers: Int? = null,
    val avgDeal: String? = null,
    val trend: List<Float>? = null,   // 5-quarter mini bars
    // Donut
    val sessions: String? = null,
    val bounceRate: String? = null,
    val avgDepth: String? = null,
    val topDevice: String? = null,
    val insight: String
)

// ── Line / Area series ────────────────────────────────────────────────────────

data class LineSeries(
    val label: String,
    val color: Color,
    val dashed: Boolean = false,
    val points: List<Float>
)

// ── Bar series ────────────────────────────────────────────────────────────────

data class BarSeries(
    val label: String,
    val color: Color,
    val values: List<Float>
)

data class HBarEntry(
    val label: String,
    val value: Float,
    val color: Color
)

// ── Sample data ───────────────────────────────────────────────────────────────

object DashboardSampleData {

    val metrics = listOf(
        MetricCardData("Total revenue",     "$1.24M",  "▲ 12.4% vs last yr", true),
        MetricCardData("Avg monthly users", "38.2K",   "▲ 8.1%",             true),
        MetricCardData("Conversion rate",   "4.7%",    "▼ 0.3 pts",          false),
        MetricCardData("NPS score",         "71",      "→ stable",            null)
    )

    val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")

    val revenueSeries = listOf(
        LineSeries("Product A", Color(0xFF378ADD), points = listOf(42f,55f,48f,62f,70f,66f,75f,80f,72f,85f,90f,95f)),
        LineSeries("Product B", Color(0xFF1D9E75), points = listOf(30f,35f,40f,38f,45f,50f,48f,52f,58f,62f,68f,74f)),
        LineSeries("Product C", Color(0xFFD85A30), dashed = true,
            points = listOf(20f,22f,18f,25f,28f,24f,30f,27f,32f,35f,30f,38f))
    )

    val barSeries = listOf(
        BarSeries("Hardware", Color(0xFF7F77DD), listOf(120f,145f,130f,160f,175f,168f,185f,190f,178f,200f,215f,230f)),
        BarSeries("Software", Color(0xFFEF9F27), listOf(85f,90f,100f,95f,115f,120f,118f,130f,125f,140f,148f,155f))
    )

    val pieSegments = listOf(
        PieSegment("Enterprise", 38f, Color(0xFF378ADD),
            PieSegmentDetails(revenue="$471K", growth="+14%", customers=42, avgDeal="$11.2K",
                trend=listOf(30f,33f,35f,36f,38f),
                insight="Largest and fastest-growing segment. Enterprise renewals up 18% YoY.")),
        PieSegment("Mid-market", 28f, Color(0xFF7F77DD),
            PieSegmentDetails(revenue="$347K", growth="+9%", customers=118, avgDeal="$2.9K",
                trend=listOf(22f,24f,25f,27f,28f),
                insight="Stable segment with strong pipeline. Upsell potential in 34 accounts.")),
        PieSegment("SMB", 20f, Color(0xFF1D9E75),
            PieSegmentDetails(revenue="$248K", growth="+5%", customers=390, avgDeal="$636",
                trend=listOf(18f,18f,19f,20f,20f),
                insight="High volume, lower deal size. Churn slightly elevated at 6.2% this quarter.")),
        PieSegment("Startup", 9f, Color(0xFFEF9F27),
            PieSegmentDetails(revenue="$112K", growth="+22%", customers=85, avgDeal="$1.3K",
                trend=listOf(5f,6f,7f,8f,9f),
                insight="Fastest growth rate. Early-stage accounts with high expansion potential.")),
        PieSegment("Non-profit", 5f, Color(0xFFD4537E),
            PieSegmentDetails(revenue="$62K", growth="+2%", customers=31, avgDeal="$2K",
                trend=listOf(4f,4f,5f,5f,5f),
                insight="Steady contribution. Eligible for the Nonprofit Pricing Program."))
    )

    val donutSegments = listOf(
        PieSegment("< 1 min",   18f, Color(0xFFD85A30),
            PieSegmentDetails(sessions="6.9K", bounceRate="72%", avgDepth="1.2 pages", topDevice="Mobile 81%",
                insight="High exit rate. Most arrive from social links. Consider landing page optimisation.")),
        PieSegment("1–3 min",   25f, Color(0xFFEF9F27),
            PieSegmentDetails(sessions="9.6K", bounceRate="38%", avgDepth="2.8 pages", topDevice="Mobile 64%",
                insight="Engaged short sessions. Likely browsing product pages. Push to account creation.")),
        PieSegment("3–10 min",  32f, Color(0xFF378ADD),
            PieSegmentDetails(sessions="12.3K", bounceRate="14%", avgDepth="5.1 pages", topDevice="Desktop 58%",
                insight="Core engaged cohort. Highest conversion rate at 8.4%. Focus retention here.")),
        PieSegment("10–30 min", 17f, Color(0xFF7F77DD),
            PieSegmentDetails(sessions="6.5K", bounceRate="6%", avgDepth="9.4 pages", topDevice="Desktop 74%",
                insight="Power users — likely existing customers or evaluating a purchase decision.")),
        PieSegment("> 30 min",  8f,  Color(0xFF1D9E75),
            PieSegmentDetails(sessions="3.1K", bounceRate="3%", avgDepth="18.2 pages", topDevice="Desktop 85%",
                insight="Highly engaged. Likely support or docs users. Consider contextual help nudges."))
    )

    val growthSeries = listOf(
        LineSeries("Users",    Color(0xFF378ADD), points = listOf(18f,22f,26f,30f,34f,40f,45f,50f,56f,62f,68f,75f)),
        LineSeries("Sessions", Color(0xFF1D9E75), points = listOf(45f,55f,64f,75f,85f,100f,112f,125f,140f,155f,172f,188f))
    )

    val hBarEntries = listOf(
        HBarEntry("Android",         42f, Color(0xFF378ADD)),
        HBarEntry("iOS",             38f, Color(0xFF7F77DD)),
        HBarEntry("Web — Chrome",    28f, Color(0xFF1D9E75)),
        HBarEntry("Web — Safari",    18f, Color(0xFFD85A30)),
        HBarEntry("Web — Firefox",    8f, Color(0xFFEF9F27)),
        HBarEntry("Desktop app",     12f, Color(0xFFD4537E)),
        HBarEntry("Tablet",           6f, Color(0xFF888780))
    )
}
