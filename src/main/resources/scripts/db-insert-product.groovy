// 신규 상품 등록
def code = input.get("code")
def name = input.get("name")
def price = input.get("price") as BigDecimal
def quantity = input.get("quantity") as Integer
def category = input.get("category")

// 유효성 검증
def errors = []
if (!code) errors.add("code is required")
if (!name) errors.add("name is required")
if (price == null || price <= 0) errors.add("price must be positive")
if (quantity == null || quantity < 0) errors.add("quantity must be non-negative")

if (!errors.isEmpty()) {
    output.put("success", false)
    output.put("errors", errors)
    output.setResult(false)
    return false
}

// 중복 체크
def existing = db.queryForList("SELECT id FROM product WHERE code = ?", code)
if (!existing.isEmpty()) {
    output.put("success", false)
    output.put("errors", ["Product with code '" + code + "' already exists"])
    output.setResult(false)
    return false
}

// INSERT 실행
def insertedRows = db.update(
    "INSERT INTO product (code, name, price, quantity, category) VALUES (?, ?, ?, ?, ?)",
    code, name, price, quantity, category
)

output.put("success", true)
output.put("insertedRows", insertedRows)
output.put("product", [code: code, name: name, price: price, quantity: quantity, category: category])
output.setResult(true)

return true
