// 재고 업데이트 (증가/감소)
def code = input.get("code")
def adjustment = input.get("adjustment") as Integer  // 양수: 증가, 음수: 감소

if (!code) {
    output.put("success", false)
    output.put("error", "code is required")
    return false
}

if (adjustment == null) {
    output.put("success", false)
    output.put("error", "adjustment is required")
    return false
}

// 현재 재고 조회
def products = db.queryForList("SELECT id, quantity FROM product WHERE code = ?", code)
if (products.isEmpty()) {
    output.put("success", false)
    output.put("error", "Product not found: " + code)
    return false
}

def currentQuantity = products[0].quantity as Integer
def newQuantity = currentQuantity + adjustment

// 재고 부족 체크
if (newQuantity < 0) {
    output.put("success", false)
    output.put("error", "Insufficient stock. Current: " + currentQuantity + ", Requested: " + adjustment)
    output.put("currentQuantity", currentQuantity)
    return false
}

// UPDATE 실행
db.update("UPDATE product SET quantity = ? WHERE code = ?", newQuantity, code)

output.put("success", true)
output.put("code", code)
output.put("previousQuantity", currentQuantity)
output.put("adjustment", adjustment)
output.put("newQuantity", newQuantity)
output.setResult(newQuantity)

return newQuantity
