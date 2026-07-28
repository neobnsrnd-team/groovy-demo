// Business logic: Validate order data
def orderId = input.get("orderId")
def items = input.get("items") as List
def totalAmount = input.get("totalAmount") as BigDecimal

def errors = []

if (!orderId) {
    errors.add("Order ID is required")
}

if (!items || items.isEmpty()) {
    errors.add("Order must have at least one item")
}

if (totalAmount == null || totalAmount <= 0) {
    errors.add("Total amount must be greater than 0")
}

def isValid = errors.isEmpty()

output.put("orderId", orderId)
output.put("isValid", isValid)
output.put("errors", errors)
output.setResult(isValid)

return isValid
