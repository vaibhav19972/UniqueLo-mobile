package com.uniquelo.app.model

/**
 * Domain models for the UniqueLo catalogue.
 *
 * These are deliberately independent of the Supabase DTOs so the screens can be
 * previewed and tested without a network connection. Every catalogue row from
 * `products`, `variants`, `product_images`, `embroidery_details`, `reviews`,
 * `universes`, `categories`, `collections` and `motifs` is folded into [Product].
 */

data class ThemeTokens(
    val bg: String? = null,
    val ink: String? = null,
    val accent: String? = null,
    val accent2: String? = null,
    val surface: String? = null,
    val productZone: String? = null,
) {
    val isEmpty: Boolean
        get() = bg == null && ink == null && accent == null && accent2 == null
}

data class Universe(
    val slug: String,
    val name: String,
    val tagline: String,
    val lore: String,
    val dropCadence: String?,
    val theme: ThemeTokens,
    val orderNum: Int,
)

data class Category(
    val slug: String,
    val name: String,
    val label: String,
    val description: String,
)

data class Collection(
    val slug: String,
    val name: String,
    val tagline: String?,
    val description: String?,
    val featured: Boolean,
)

data class Motif(
    val id: String,
    val universeSlug: String?,
    val name: String,
    val meaning: String,
    val symbolNotes: String?,
)

data class Variant(
    val sku: String,
    val productSlug: String,
    val color: String,
    val colorHex: String,
    val size: String,
    val priceCents: Int,
    val compareAtPriceCents: Int?,
    val status: String,
    val quantity: Int,
) {
    val inStock: Boolean
        get() = status.equals("in-stock", ignoreCase = true) ||
            status.equals("low-stock", ignoreCase = true)

    val lowStock: Boolean
        get() = inStock && quantity in 1..5

    val statusLabel: String
        get() = when {
            !inStock -> "Sold out"
            lowStock -> "Only $quantity left"
            else -> "In stock"
        }
}

data class VariantColor(
    val name: String,
    val hex: String,
)

data class ProductImage(
    val src: String,
    val alt: String,
    val color: String?,
    val sortOrder: Int,
) {
    val isRemote: Boolean
        get() = src.startsWith("http://") || src.startsWith("https://")
}

data class Embroidery(
    val technique: String,
    val techniqueLabel: String,
    val artisanHours: Int?,
    val placement: List<String>,
    val threadComposition: String,
    val motifStory: String,
    val macroImageSrc: String?,
    val macroImageAlt: String?,
)

data class Review(
    val id: String,
    val productSlug: String,
    val author: String,
    val rating: Int,
    val title: String,
    val comment: String,
    val verified: Boolean,
)

data class Product(
    val slug: String,
    val name: String,
    val subtitle: String,
    val foreignTagline: String?,
    val description: String,
    val categorySlug: String?,
    val universeSlug: String?,
    val collectionSlug: String?,
    val garmentType: String?,
    val gender: String?,
    val motifId: String?,
    val handcrafted: Boolean,
    val craftTechniques: List<String>,
    val edits: List<String>,
    val expressionScore: Int?,
    val rating: Double,
    val reviewCount: Int,
    val soldCount: String?,
    val tags: List<String>,
    val featured: Boolean,
    val customizable: Boolean,
    val material: List<String>,
    val care: List<String>,
    val fit: String?,
    val batchNumber: Int?,
    val batchTotal: Int?,
    val craftRegion: String?,
    val craftCluster: String?,
    val fabricGsm: String?,
    val variants: List<Variant>,
    val images: List<ProductImage>,
    val embroidery: Embroidery?,
    val reviews: List<Review>,
    val universe: Universe?,
    val category: Category?,
    val collection: Collection?,
    val motif: Motif?,
) {
    val remoteImages: List<ProductImage>
        get() = images.filter { it.isRemote }.sortedBy { it.sortOrder }

    val primaryImageUrl: String?
        get() = remoteImages.firstOrNull()?.src

    val minPriceCents: Int
        get() = variants.minOfOrNull { it.priceCents } ?: 0

    val maxPriceCents: Int
        get() = variants.maxOfOrNull { it.priceCents } ?: minPriceCents

    val priceLabel: String
        get() = if (minPriceCents != maxPriceCents) {
            "From ${formatCents(minPriceCents)}"
        } else {
            formatCents(minPriceCents)
        }

    val hasStock: Boolean
        get() = variants.any { it.inStock }

    val colors: List<VariantColor>
        get() = variants
            .map { VariantColor(it.color, it.colorHex) }
            .distinctBy { it.name }

    val sizes: List<String>
        get() = variants.map { it.size }.distinct()

    val editionLabel: String?
        get() = if (batchNumber != null && batchTotal != null) {
            "Batch ${batchNumber.toString().padStart(2, '0')} / $batchTotal"
        } else {
            null
        }

    val categoryLabel: String
        get() = category?.label ?: category?.name ?: garmentType?.replaceFirstChar { it.uppercase() } ?: "Piece"

    val universeName: String
        get() = universe?.name ?: "UniqueLo"

    val collectionName: String?
        get() = collection?.name

    val ratingLabel: String
        get() = if (reviewCount > 0) rating.toString().trimEnd('0').trimEnd('.') else "New"

    val isLowStock: Boolean
        get() = variants.any { it.lowStock }
}

data class CartLine(
    val product: Product,
    val variant: Variant,
    val quantity: Int,
) {
    val lineTotalCents: Int
        get() = variant.priceCents * quantity
}

/** Prices in the catalogue are stored as paise (1/100 of an Indian rupee). */
fun formatCents(cents: Int): String {
    val safe = cents.coerceAtLeast(0)
    val rupees = safe / 100
    val paise = safe % 100
    val grouped = groupIndian(rupees.toLong())
    return if (paise == 0) {
        "₹$grouped"
    } else {
        "₹$grouped.${paise.toString().padStart(2, '0')}"
    }
}

/** 1234567 -> "12,34,567" (Indian digit grouping). */
fun groupIndian(value: Long): String {
    val digits = value.toString()
    if (digits.length <= 3) return digits
    val tail = digits.takeLast(3)
    var head = digits.dropLast(3)
    val groups = mutableListOf<String>()
    while (head.length > 2) {
        groups.add(0, head.takeLast(2))
        head = head.dropLast(2)
    }
    if (head.isNotEmpty()) groups.add(0, head)
    return groups.joinToString(",") + "," + tail
}
