package com.monetization.ikadplugin.subscription


data class SubscriptionPlanModel(
    val title: String = "",
    val desc: String = "",
    var price: String = "",
    var isSelected: Boolean = false
)
