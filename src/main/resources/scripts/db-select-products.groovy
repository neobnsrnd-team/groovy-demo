// DB에서 상품 목록 조회
def category = input.get("category")
def limit = input.get("limit") ?: 10

def sql
def params

if (category) {
    sql = "SELECT * FROM product WHERE category = ? ORDER BY created_at DESC LIMIT ?"
    params = [category, limit] as Object[]
} else {
    sql = "SELECT * FROM product ORDER BY created_at DESC LIMIT ?"
    params = [limit] as Object[]
}

def products = db.queryForList(sql, params)

output.put("count", products.size())
output.put("products", products)
output.setResult(products)

return products
