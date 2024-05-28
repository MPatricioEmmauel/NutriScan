package com.example.nutriscan

import com.google.mlkit.vision.barcode.Barcode
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface API {
    @GET("api/v2/product/{barcode}")
    fun getProductByBarcode(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = "product_name,nutrition_grades"
    ): Call<ProductResponse>
}