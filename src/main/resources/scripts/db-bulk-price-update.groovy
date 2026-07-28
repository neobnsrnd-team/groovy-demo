// 카테고리별 일괄 가격 조정
def category = input.get("category")
def adjustmentPercent = input.get("adjustmentPercent") as BigDecimal  // 예: 10 = 10% 인상, -5 = 5% 할인

if (!category) {
    output.put("success", false)
    output.put("error", "category is required")
    return false
}

if (adjustmentPercent == null) {
    output.put("success", false)
    output.put("error", "adjustmentPercent is required")
    return false
}

// 조정 전 가격 조회
def beforeProducts = db.queryForList(
    "SELECT code, name, price FROM product WHERE category = ?",
    category
)

if (beforeProducts.isEmpty()) {
    output.put("success", false)
    output.put("error", "No products found in category: " + category)
    return false
}

// 가격 조정 계산
def multiplier = 1 + (adjustmentPercent / 100)

// UPDATE 실행
def updatedRows = db.update(
    "UPDATE product SET price = ROUND(price * ?, 2) WHERE category = ?",
    multiplier, category
)

// 조정 후 가격 조회
def afterProducts = db.queryForList(
    "SELECT code, name, price FROM product WHERE category = ?",
    category
)

// 변경 내역 생성
def changes = []
beforeProducts.eachWithIndex { before, idx ->
    def after = afterProducts[idx]
    changes.add([
        code: before.code,
        name: before.name,
        priceBefore: before.price,
        priceAfter: after.price,
        change: after.price - before.price
    ])
}

output.put("success", true)
output.put("category", category)
output.put("adjustmentPercent", adjustmentPercent)
output.put("updatedCount", updatedRows)
output.put("changes", changes)
output.setResult(changes)

return changes
