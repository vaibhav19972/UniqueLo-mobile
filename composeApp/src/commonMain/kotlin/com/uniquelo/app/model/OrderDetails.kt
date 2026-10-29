package com.uniquelo.app.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalOrderSummary(
    val id: String,
    val code: String,
    val email: String,
    val totalCents: Int = 0,
    val itemCount: Int = 1,
    val timestampIso: String = "",
)

data class OrderDetails(
    val id: String,
    val code: String,
    val customerName: String,
    val customerEmail: String,
    val status: String,
    val subtotalCents: Int,
    val discountCents: Int,
    val shippingCents: Int,
    val totalCents: Int,
    val createdAt: String?,
    val items: List<OrderItemDetail>,
)

data class OrderItemDetail(
    val id: String,
    val productSlug: String,
    val variantSku: String,
    val productName: String,
    val color: String,
    val size: String,
    val quantity: Int,
    val priceCents: Int,
)

