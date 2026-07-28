// 카테고리별 재고 가치 계산
def category = input.get("category")

def sql
def params

if (category) {
    sql = """
        SELECT
            category,
            COUNT(*) as product_count,
            SUM(quantity) as total_quantity,
            SUM(price * quantity) as total_value,
            AVG(price) as avg_price
        FROM product
        WHERE category = ?
        GROUP BY category
    """
    params = [category] as Object[]
} else {
    sql = """
        SELECT
            category,
            COUNT(*) as product_count,
            SUM(quantity) as total_quantity,
            SUM(price * quantity) as total_value,
            AVG(price) as avg_price
        FROM product
        GROUP BY category
        ORDER BY total_value DESC
    """
    params = [] as Object[]
}

def stats = db.queryForList(sql, params)

// 전체 합계 계산
def grandTotal = stats.inject(0.0) { sum, row ->
    sum + (row.total_value ?: 0)
}

output.put("categoryStats", stats)
output.put("grandTotal", grandTotal)
output.put("categoryCount", stats.size())
output.setResult(stats)

return stats
