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
