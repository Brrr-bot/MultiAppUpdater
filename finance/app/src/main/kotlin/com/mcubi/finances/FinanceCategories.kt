package com.mcubi.finances

object FinanceCategories {
    val income = listOf("Salary", "Freelance", "Gift", "Other Income")
    val expense = listOf(
        "Food & Drink", "Groceries", "Grab", "Shopee", "Transport", "Bills",
        "Shopping", "Health", "Entertainment", "Rent", "Other"
    )

    fun normalize(direction: String, description: String, category: String): String {
        if (direction != "out") return category
        val text = description.lowercase()
        return when {
            "grab" in text -> "Grab"
            "shopee" in text || "shoppee" in text -> "Shopee"
            else -> category
        }
    }
}
