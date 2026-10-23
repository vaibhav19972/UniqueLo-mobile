package com.uniquelo.app.data

import com.uniquelo.app.model.Category
import com.uniquelo.app.model.Collection
import com.uniquelo.app.model.Embroidery
import com.uniquelo.app.model.Motif
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.ProductImage
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.ThemeTokens
import com.uniquelo.app.model.Universe
import com.uniquelo.app.model.Variant

internal fun ThemeTokensDto.toDomain() = ThemeTokens(
    bg = bg,
    ink = ink,
    accent = accent,
    accent2 = accent2,
    surface = surface,
    productZone = productZone,
)

internal fun UniverseDto.toDomain() = Universe(
    slug = slug,
    name = name,
    tagline = tagline,
    lore = lore,
    dropCadence = dropCadence,
    theme = themeTokens?.toDomain() ?: ThemeTokens(),
    orderNum = orderNum ?: 0,
)

internal fun CategoryDto.toDomain() = Category(
    slug = slug,
    name = name,
    label = label.ifBlank { name },
    description = description,
)

internal fun CollectionDto.toDomain() = Collection(
    slug = slug,
    name = name,
    tagline = tagline,
    description = description,
    featured = featured,
)

internal fun MotifDto.toDomain() = Motif(
    id = id,
    universeSlug = universeSlug,
    name = name,
    meaning = meaning,
    symbolNotes = symbolNotes,
)

internal fun VariantDto.toDomain() = Variant(
    sku = sku,
    productSlug = productSlug,
    color = color,
    colorHex = colorHex,
    size = size,
    priceCents = priceCents,
    compareAtPriceCents = compareAtPriceCents,
    status = status,
    quantity = quantity,
)

internal fun ProductImageDto.toDomain() = ProductImage(
    src = src,
    alt = alt,
    color = color,
    sortOrder = sortOrder,
)

internal fun EmbroideryDto.toDomain() = Embroidery(
    technique = technique,
    techniqueLabel = techniqueLabel,
    artisanHours = artisanHours,
    placement = placement,
    threadComposition = threadComposition,
    motifStory = motifStory,
    macroImageSrc = macroImageSrc,
    macroImageAlt = macroImageAlt,
)

internal fun ReviewDto.toDomain() = Review(
    id = id,
    productSlug = productSlug,
    author = author,
    rating = rating,
    title = title,
    comment = comment,
    verified = verified,
)

internal fun ProductDto.toDomain(
    universe: Universe?,
    category: Category?,
    collection: Collection?,
    motif: Motif?,
    reviews: List<Review>,
) = Product(
    slug = slug,
    name = name.trim(),
    subtitle = subtitle.trim(),
    foreignTagline = foreignTagline?.trim(),
    description = description.trim(),
    categorySlug = categorySlug,
    universeSlug = universeSlug,
    collectionSlug = collectionSlug,
    garmentType = garmentType,
    gender = gender,
    motifId = motifId,
    handcrafted = handcrafted,
    craftTechniques = craftTechniques,
    edits = edits,
    expressionScore = expressionScore,
    rating = rating,
    reviewCount = reviewCount,
    soldCount = soldCount,
    tags = tags,
    featured = featured,
    customizable = customizable,
    material = material,
    care = care,
    fit = fit?.trim(),
    batchNumber = batchNumber,
    batchTotal = batchTotal,
    craftRegion = craftRegion?.trim(),
    craftCluster = craftCluster?.trim(),
    fabricGsm = fabricGsm,
    variants = variants.map { it.toDomain() },
    images = productImages.map { it.toDomain() }.sortedBy { it.sortOrder },
    embroidery = embroideryDetails?.toDomain(),
    reviews = reviews,
    universe = universe,
    category = category,
    collection = collection,
    motif = motif,
)
