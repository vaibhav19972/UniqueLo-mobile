package com.uniquelo.app.data

import com.uniquelo.app.model.CartLine
import com.uniquelo.app.model.Category
import com.uniquelo.app.model.Collection
import com.uniquelo.app.model.Motif
import com.uniquelo.app.model.Product
import com.uniquelo.app.model.Review
import com.uniquelo.app.model.Universe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

data class CatalogSnapshot(
    val products: List<Product>,
    val universes: List<Universe>,
    val categories: List<Category>,
    val collections: List<Collection>,
    val motifs: List<Motif>,
    val reviews: List<Review>,
)

data class CheckoutForm(
    val name: String,
    val email: String,
    val addressLine: String,
    val city: String,
    val postalCode: String,
    val country: String = "India",
)

data class PlacedOrder(
    val id: String,
    val code: String,
    val email: String,
)

class SupabaseException(message: String) : Exception(message)

interface UniqueLoRepository {
    suspend fun loadCatalog(): CatalogSnapshot
    suspend fun createOrder(form: CheckoutForm, lines: List<CartLine>): PlacedOrder
    suspend fun subscribe(email: String)
}

/**
 * Thin PostgREST client. Six catalogue reads and three writes are needed for the
 * MVP, so the Ktor client talks to Supabase directly instead of pulling in a
 * full auth/realtime SDK.
 */
class SupabaseRepository(
    private val client: HttpClient = createSupabaseHttpClient(),
) : UniqueLoRepository {

    override suspend fun loadCatalog(): CatalogSnapshot = coroutineScope {
        val universesRequest = async {
            getRows<UniverseDto>("universes", order = "order_num.asc.nullslast")
        }
        val categoriesRequest = async {
            getRows<CategoryDto>("categories", order = "order_num.asc.nullslast")
        }
        val collectionsRequest = async {
            getRows<CollectionDto>("collections", order = "order_num.asc.nullslast")
        }
        val motifsRequest = async {
            getRows<MotifDto>("motifs", order = "created_at.asc")
        }
        val reviewsRequest = async {
            getRows<ReviewDto>("reviews", order = "created_at.desc")
        }
        val productsRequest = async {
            getRows<ProductDto>(
                path = "products",
                select = "*,variants(*),product_images(*),embroidery_details(*)",
                order = "created_at.asc",
            )
        }

        val universes = universesRequest.await().map { it.toDomain() }
        val categories = categoriesRequest.await().map { it.toDomain() }
        val collections = collectionsRequest.await().map { it.toDomain() }
        val motifs = motifsRequest.await().map { it.toDomain() }
        val reviews = reviewsRequest.await().map { it.toDomain() }

        val universeBySlug = universes.associateBy { it.slug }
        val categoryBySlug = categories.associateBy { it.slug }
        val collectionBySlug = collections.associateBy { it.slug }
        val motifById = motifs.associateBy { it.id }
        val reviewsBySlug = reviews.groupBy { it.productSlug }

        val products = productsRequest.await().map { dto ->
            dto.toDomain(
                universe = dto.universeSlug?.let(universeBySlug::get),
                category = dto.categorySlug?.let(categoryBySlug::get),
                collection = dto.collectionSlug?.let(collectionBySlug::get),
                motif = dto.motifId?.let(motifById::get),
                reviews = reviewsBySlug[dto.slug].orEmpty(),
            )
        }

        CatalogSnapshot(
            products = products,
            universes = universes,
            categories = categories,
            collections = collections,
            motifs = motifs,
            reviews = reviews,
        )
    }

    override suspend fun createOrder(form: CheckoutForm, lines: List<CartLine>): PlacedOrder {
        require(lines.isNotEmpty()) { "The bag is empty." }
        require(form.name.isNotBlank()) { "Please add a name." }
        require(form.email.isNotBlank()) { "Please add an email address." }

        val orderId = newUuid()
        val subtotal = lines.sumOf { it.lineTotalCents }

        val order = OrderInsert(
            id = orderId,
            customerName = form.name.trim(),
            customerEmail = form.email.trim().lowercase(),
            status = "reserved",
            subtotalCents = subtotal,
            discountCents = 0,
            shippingCents = 0,
            totalCents = subtotal,
            shippingAddress = mapOf(
                "line1" to form.addressLine.trim(),
                "city" to form.city.trim(),
                "postal_code" to form.postalCode.trim(),
                "country" to form.country.trim(),
            ),
        )

        post("orders", order)

        val items = lines.map { line ->
            OrderItemInsert(
                id = newUuid(),
                orderId = orderId,
                productSlug = line.product.slug,
                variantSku = line.variant.sku,
                productName = line.product.name,
                color = line.variant.color,
                size = line.variant.size,
                quantity = line.quantity,
                priceCents = line.variant.priceCents,
            )
        }
        post("order_items", items)

        return PlacedOrder(
            id = orderId,
            code = "UL-" + orderId.replace("-", "").take(8).uppercase(),
            email = order.customerEmail,
        )
    }

    override suspend fun subscribe(email: String) {
        val clean = email.trim().lowercase()
        require(clean.isNotBlank()) { "Please add an email address." }
        post("newsletter_subscribers", NewsletterInsert(email = clean))
    }

    private suspend inline fun <reified T> getRows(
        path: String,
        select: String = "*",
        order: String? = null,
    ): List<T> {
        val response = client.get("${SupabaseConfig.REST_URL}/$path") {
            parameter("select", select)
            if (order != null) parameter("order", order)
        }
        ensureSuccess(response)
        return response.body()
    }

    private suspend inline fun <reified T> post(path: String, payload: T) {
        val response = client.post("${SupabaseConfig.REST_URL}/$path") {
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        ensureSuccess(response)
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val raw = response.bodyAsText()
        throw SupabaseException(readError(raw, response.status.value))
    }

    private fun readError(raw: String, status: Int): String {
        val apiMessage = runCatching {
            SupabaseJson.parseToJsonElement(raw).jsonObject["message"]
                ?.let { (it as? JsonPrimitive)?.content }
        }.getOrNull()
        return apiMessage?.takeIf { it.isNotBlank() }
            ?: "The atelier service returned an error ($status)."
    }
}

private fun newUuid(): String {
    val bytes = ByteArray(16) { Random.nextInt(256).toByte() }
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()

    val hex = "0123456789abcdef"
    return buildString(36) {
        bytes.forEachIndexed { index, byte ->
            if (index == 4 || index == 6 || index == 8 || index == 10) append('-')
            val value = byte.toInt() and 0xFF
            append(hex[value ushr 4])
            append(hex[value and 0x0F])
        }
    }
}
