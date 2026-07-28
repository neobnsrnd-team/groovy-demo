// Business logic: Calculate discount based on amount and customer type
def amount = input.get("amount") as BigDecimal
def customerType = input.get("customerType") as String

def discountRate = switch(customerType) {
    case "VIP" -> 0.20
    case "GOLD" -> 0.15
    case "SILVER" -> 0.10
    default -> 0.05
}

def discountAmount = amount * discountRate
def finalAmount = amount - discountAmount

output.put("originalAmount", amount)
output.put("discountRate", discountRate)
output.put("discountAmount", discountAmount)
output.put("finalAmount", finalAmount)
output.setResult(finalAmount)

return finalAmount
