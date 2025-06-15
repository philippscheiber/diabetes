package com.example.diabetesapp.network

import retrofit2.http.GET
import retrofit2.http.Path

data class Nutriments(
    val carbohydrates_100g: Double?
)

data class Product(
    val product_name: String?,
    val nutriments: Nutriments?
)

data class ProductResponse(
    val status: Int,
    val product: Product?
)

interface OpenFoodFactsService {
    @GET("api/v0/product/{barcode}.json")
    suspend fun getProduct(@Path("barcode") code: String): ProductResponse
}
