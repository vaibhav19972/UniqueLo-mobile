package com.uniquelo.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductDto(
    val slug: String,
    val name: String = "",
    val subtitle: String = "",
    @SerialName("foreign_tagline") val foreignTagline: String? = null,
    val description: String = "",
    @SerialName("category_slug") val categorySlug: String? = null,
    @SerialName("universe_slug") val universeSlug: String? = null,
    @SerialName("collection_slug") val collectionSlug: String? = null,
    @SerialName("garment_type") val garmentType: String? = null,
    val gender: String? = null,
    @SerialName("motif_id") val motifId: String? = null,
    val handcrafted: Boolean = false,
    @SerialName("craft_techniques") val craftTechniques: List<String> = emptyList(),
    val edits: List<String> = emptyList(),
    @SerialName("expression_score") val expressionScore: Int? = null,
    @SerialName("expression_slug") val expressionSlug: String? = null,
    val rating: Double = 0.0,
    @SerialName("review_count") val reviewCount: Int = 0,
    @SerialName("sold_count") val soldCount: String? = null,
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
    val customizable: Boolean = false,
    val material: List<String> = emptyList(),
    val care: List<String> = emptyList(),
    val fit: String? = null,
    @SerialName("batch_number") val batchNumber: Int? = null,
    @SerialName("batch_total") val batchTotal: Int? = null,
    @SerialName("craft_region") val craftRegion: String? = null,
    @SerialName("craft_cluster") val craftCluster: String? = null,
    @SerialName("fabric_gsm") val fabricGsm: String? = null,
    @SerialName("country_of_origin") val countryOfOrigin: String = "IN",
    @SerialName("cod_available") val codAvailable: Boolean = true,
    @SerialName("delivery_days_min") val deliveryDaysMin: Int? = 4,
    @SerialName("delivery_days_max") val deliveryDaysMax: Int? = 7,
    @SerialName("return_window_days") val returnWindowDays: Int = 7,
    @SerialName("weight_grams") val weightGrams: Int? = null,
    @SerialName("hsn_code") val hsnCode: String? = null,
    @SerialName("style_code") val styleCode: String? = null,
    @SerialName("fit_notes") val fitNotes: String? = null,
    @SerialName("model_height_cm") val modelHeightCm: Int? = null,
    @SerialName("model_worn_size") val modelWornSize: String? = null,
    @SerialName("package_length_mm") val packageLengthMm: Int? = null,
    @SerialName("package_width_mm") val packageWidthMm: Int? = null,
    @SerialName("package_height_mm") val packageHeightMm: Int? = null,
    @SerialName("manufacturer_name") val manufacturerName: String? = null,
    @SerialName("manufacturer_address") val manufacturerAddress: String? = null,
    @SerialName("packed_by") val packedBy: String? = null,
    val variants: List<VariantDto> = emptyList(),
    @SerialName("product_images") val productImages: List<ProductImageDto> = emptyList(),
    @SerialName("embroidery_details") val embroideryDetails: EmbroideryDto? = null,
)

@Serializable
data class VariantDto(
    val sku: String,
    @SerialName("product_slug") val productSlug: String = "",
    val color: String = "",
    @SerialName("color_hex") val colorHex: String = "#15150F",
    val size: String = "",
    @SerialName("price_cents") val priceCents: Int = 0,
    @SerialName("compare_at_price_cents") val compareAtPriceCents: Int? = null,
    val status: String = "in-stock",
    val quantity: Int = 0,
    val barcode: String? = null,
)

@Serializable
data class ProductImageDto(
    val id: String = "",
    @SerialName("product_slug") val productSlug: String = "",
    val src: String = "",
    val alt: String = "",
    val color: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class EmbroideryDto(
    val id: String = "",
    @SerialName("product_slug") val productSlug: String = "",
    val technique: String = "",
    @SerialName("technique_label") val techniqueLabel: String = "",
    @SerialName("artisan_hours") val artisanHours: Int? = null,
    val placement: List<String> = emptyList(),
    @SerialName("thread_composition") val threadComposition: String = "",
    @SerialName("motif_story") val motifStory: String = "",
    @SerialName("macro_image_src") val macroImageSrc: String? = null,
    @SerialName("macro_image_alt") val macroImageAlt: String? = null,
)

@Serializable
data class ReviewDto(
    val id: String,
    @SerialName("product_slug") val productSlug: String = "",
    val author: String = "",
    val rating: Int = 0,
    val title: String = "",
    val comment: String = "",
    val verified: Boolean = false,
)

@Serializable
data class UniverseDto(
    val slug: String,
    val name: String = "",
    val tagline: String = "",
    val lore: String = "",
    @SerialName("drop_cadence") val dropCadence: String? = null,
    val is_active: Boolean = true,
    @SerialName("order_num") val orderNum: Int? = null,
    @SerialName("theme_tokens") val themeTokens: ThemeTokensDto? = null,
)

@Serializable
data class ThemeTokensDto(
    val bg: String? = null,
    val ink: String? = null,
    val accent: String? = null,
    val accent2: String? = null,
    val surface: String? = null,
    @SerialName("product_zone") val productZone: String? = null,
)

@Serializable
data class CategoryDto(
    val slug: String,
    val name: String = "",
    val label: String = "",
    val description: String = "",
    val image: String? = null,
    @SerialName("order_num") val orderNum: Int? = null,
)

@Serializable
data class CollectionDto(
    val slug: String,
    val name: String = "",
    val tagline: String? = null,
    val description: String? = null,
    @SerialName("universe_slug") val universeSlug: String? = null,
    @SerialName("cover_image") val coverImage: String? = null,
    val featured: Boolean = false,
    @SerialName("order_num") val orderNum: Int? = null,
)

@Serializable
data class MotifDto(
    val id: String,
    @SerialName("universe_slug") val universeSlug: String? = null,
    val name: String = "",
    val meaning: String = "",
    @SerialName("symbol_notes") val symbolNotes: String? = null,
    @SerialName("ip_cleared") val ipCleared: Boolean? = null,
)

@Serializable
data class QuoteItemPayload(
    @SerialName("variant_sku") val variantSku: String,
    val quantity: Int,
)

@Serializable
data class QuoteOrderPayload(
    @SerialName("p_items") val items: List<QuoteItemPayload>,
    @SerialName("p_coupon_code") val couponCode: String? = null,
    @SerialName("p_customer_email") val customerEmail: String? = null,
    @SerialName("p_payment_method") val paymentMethod: String = "prepaid",
)

@Serializable
data class QuoteLine(
    val sku: String,
    val size: String = "",
    val color: String = "",
    val quantity: Int = 1,
    @SerialName("product_slug") val productSlug: String = "",
    @SerialName("stock_available") val stockAvailable: Int = 0,
    @SerialName("compare_at_cents") val compareAtCents: Int? = null,
    @SerialName("unit_price_cents") val unitPriceCents: Int = 0,
    @SerialName("line_total_cents") val lineTotalCents: Int = 0,
)

@Serializable
data class CouponResult(
    val code: String = "",
    val error: String? = null,
    val description: String? = null,
    @SerialName("value_cents") val valueCents: Int? = null,
    @SerialName("discount_type") val discountType: String? = null,
    @SerialName("discount_value") val discountValue: Int? = null,
    @SerialName("min_subtotal_cents") val minSubtotalCents: Int? = null,
) {
    val isValid: Boolean get() = error.isNullOrBlank()
}

@Serializable
data class CouponOffer(
    val code: String = "",
    val description: String = "",
    @SerialName("value_cents") val valueCents: Int = 0,
    @SerialName("discount_type") val discountType: String = "",
    @SerialName("discount_value") val discountValue: Int = 0,
    @SerialName("min_subtotal_cents") val minSubtotalCents: Int = 0,
)

@Serializable
data class QuoteResponse(
    val lines: List<QuoteLine> = emptyList(),
    val units: Int = 0,
    val coupon: CouponResult? = null,
    @SerialName("quoted_at") val quotedAt: String? = null,
    @SerialName("best_coupon") val bestCoupon: CouponOffer? = null,
    @SerialName("total_cents") val totalCents: Int = 0,
    @SerialName("cod_fee_cents") val codFeeCents: Int = 0,
    @SerialName("cod_max_units") val codMaxUnits: Int = 10,
    @SerialName("savings_cents") val savingsCents: Int = 0,
    @SerialName("discount_cents") val discountCents: Int = 0,
    @SerialName("payment_method") val paymentMethod: String = "prepaid",
    @SerialName("shipping_cents") val shippingCents: Int = 0,
    @SerialName("subtotal_cents") val subtotalCents: Int = 0,
    @SerialName("flat_rate_cents") val flatRateCents: Int = 0,
    @SerialName("available_offers") val availableOffers: List<CouponOffer> = emptyList(),
    @SerialName("identity_checked") val identityChecked: Boolean = false,
    @SerialName("free_shipping_threshold_cents") val freeShippingThresholdCents: Int = 0,
)

@Serializable
data class OrderInsert(
    val id: String,
    @SerialName("customer_name") val customerName: String,
    @SerialName("customer_email") val customerEmail: String,
    val status: String,
    @SerialName("subtotal_cents") val subtotalCents: Int,
    @SerialName("discount_cents") val discountCents: Int,
    @SerialName("shipping_cents") val shippingCents: Int,
    @SerialName("total_cents") val totalCents: Int,
    @SerialName("shipping_address") val shippingAddress: Map<String, String>,
    @SerialName("coupon_code") val couponCode: String? = null,
)

@Serializable
data class OrderItemInsert(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("product_slug") val productSlug: String,
    @SerialName("variant_sku") val variantSku: String,
    @SerialName("product_name") val productName: String,
    val color: String,
    val size: String,
    val quantity: Int,
    @SerialName("price_cents") val priceCents: Int,
)

@Serializable
data class NewsletterInsert(
    val email: String,
)

@Serializable
data class ReviewInsert(
    @SerialName("product_slug") val productSlug: String,
    val author: String,
    val rating: Int,
    val title: String,
    val comment: String,
)

@Serializable
data class OrderRecordDto(
    val id: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_email") val customerEmail: String = "",
    val status: String = "confirmed",
    @SerialName("subtotal_cents") val subtotalCents: Int = 0,
    @SerialName("discount_cents") val discountCents: Int = 0,
    @SerialName("shipping_cents") val shippingCents: Int = 0,
    @SerialName("total_cents") val totalCents: Int = 0,
    @SerialName("coupon_code") val couponCode: String? = null,
    @SerialName("gift_note") val giftNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class OrderItemRecordDto(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("product_slug") val productSlug: String = "",
    @SerialName("variant_sku") val variantSku: String = "",
    @SerialName("product_name") val productName: String = "",
    val color: String = "",
    val size: String = "",
    val quantity: Int = 1,
    @SerialName("price_cents") val priceCents: Int = 0,
    @SerialName("line_total_cents") val lineTotalCents: Int? = null,
)

@Serializable
data class OrderDetailsResponseDto(
    val order: OrderRecordDto,
    val items: List<OrderItemRecordDto> = emptyList(),
)
