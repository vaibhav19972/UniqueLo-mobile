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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

@Serializable
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

data class ReviewDraft(
    val productSlug: String,
    val author: String,
    val rating: Int,
    val title: String,
    val comment: String,
)

data class PlacedOrder(
    val id: String,
    val code: String,
    val email: String,
)

class SupabaseException(message: String, val status: Int = 0) : Exception(message) {
    /** PostgREST reports a missing RPC as 404/PGRST202 (or 501 on some versions). */
    val isMissingRpcFunction: Boolean
        get() = status == 404 || status == 501
}

/** Payload for the atomic `place_order` Postgres function. */
@Serializable
private data class PlaceOrderPayload(
    @SerialName("p_order") val order: OrderInsert,
    @SerialName("p_items") val items: List<OrderItemInsert>,
)

interface UniqueLoRepository {
    suspend fun loadCatalog(): CatalogSnapshot
    suspend fun createOrder(form: CheckoutForm, lines: List<CartLine>): PlacedOrder
    suspend fun subscribe(email: String)
    suspend fun submitReview(review: ReviewDraft)
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

        try {
            // Preferred path: one atomic Postgres function writes the order
            // header and its items in a single transaction. The SQL lives in
            // supabase/migrations.
            val response = client.post("${SupabaseConfig.REST_URL}/rpc/place_order") {
                contentType(ContentType.Application.Json)
                setBody(PlaceOrderPayload(order = order, items = items))
            }
            ensureSuccess(response)
            // Drain the response so Ktor can return the connection to the pool.
            // The function's return value is intentionally unused.
            response.bodyAsText()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            if (throwable is SupabaseException && throwable.isMissingRpcFunction) {
                // The project has not applied the migration yet — fall back to
                // the legacy two-call write, with a compensating delete so a
                // half-written order is never left behind.
                createOrderLegacy(order, items, orderId)
            } else {
                throw throwable
            }
        }

        return PlacedOrder(
            id = orderId,
            code = "UL-" + orderId.replace("-", "").take(8).uppercase(),
            email = order.customerEmail,
        )
    }

    private suspend fun createOrderLegacy(
        order: OrderInsert,
        items: List<OrderItemInsert>,
        orderId: String,
    ) {
        post("orders", order)
        try {
            post("order_items", items)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            // Roll back the orphaned header row on any item failure; ignore
            // rollback failures (RLS may forbid DELETE) and surface the
            // original cause. Best-effort until place_order is deployed.
            runCatching {
                val response = client.delete("${SupabaseConfig.REST_URL}/orders") {
                    parameter("id", "eq.$orderId")
                }
                response.bodyAsText()
            }
            throw throwable
        }
    }

    override suspend fun subscribe(email: String) {
        val clean = email.trim().lowercase()
        require(clean.isNotBlank()) { "Please add an email address." }
        post("newsletter_subscribers", NewsletterInsert(email = clean))
    }

    override suspend fun submitReview(review: ReviewDraft) {
        val productSlug = review.productSlug.trim()
        val author = review.author.trim()
        val title = review.title.trim()
        val comment = review.comment.trim()
        require(productSlug.isNotBlank()) { "Product is required." }
        require(review.rating in 1..5) { "Rating must be between 1 and 5." }
        require(author.isNotBlank()) { "Please add a name." }
        require(title.isNotBlank()) { "Please add a headline." }
        require(comment.isNotBlank()) { "Please tell us what you think." }

        post(
            "reviews",
            ReviewInsert(
                productSlug = productSlug,
                author = author,
                rating = review.rating,
                title = title,
                comment = comment,
            ),
        )
    }

    private suspend inline fun <reified T> getRows(
        path: String,
        select: String = "*",
        order: String? = null,
    ): List<T> {
        // Bounded exponential backoff for transient read failures (connect /
        // timeout / 5xx). 4xx fails fast — retrying won't change the outcome.
        var delayMs = 400L
        repeat(2) {
            try {
                val response = client.get("${SupabaseConfig.REST_URL}/$path") {
                    parameter("select", select)
                    if (order != null) parameter("order", order)
                }
                ensureSuccess(response)
                return response.body<List<T>>()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (error is SupabaseException && error.status < 500) throw error
                delay(delayMs)
                delayMs *= 2
            }
        }
        val response = client.get("${SupabaseConfig.REST_URL}/$path") {
            parameter("select", select)
            if (order != null) parameter("order", order)
        }
        ensureSuccess(response)
        return response.body<List<T>>()
    }

    private suspend inline fun <reified T> post(path: String, payload: T) {
        val response = client.post("${SupabaseConfig.REST_URL}/$path") {
            header("Prefer", "return=minimal")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        ensureSuccess(response)
        // Consume the (empty) body so the connection is released.
        response.bodyAsText()
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.value in 200..299) return
        val raw = response.bodyAsText()
        throw SupabaseException(readError(raw, response.status.value), response.status.value)
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
