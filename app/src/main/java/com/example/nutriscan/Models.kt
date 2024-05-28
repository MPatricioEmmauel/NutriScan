package com.example.nutriscan

data class ProductResponse(
    val code: String,
    val product: Product?,
    val status: Int,
    val status_verbose:String
)

data class Product(
    val nutrition_grades: String?,
    val product_name:String?
)
