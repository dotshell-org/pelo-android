package com.pelotcl.app.generic.data.models

import com.google.gson.annotations.SerializedName

data class SiriData(
    @SerializedName("Siri")
    val siri: Siri?
)