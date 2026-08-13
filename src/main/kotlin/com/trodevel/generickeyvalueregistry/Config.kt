package com.trodevel.generickeyvalueregistry

data class Config(
    val is_active: Boolean,
    val allow_missing_file: Boolean,
    val filename: String,
    val must_expire_keys: Boolean,
    val expiration_period_days: Int
)
