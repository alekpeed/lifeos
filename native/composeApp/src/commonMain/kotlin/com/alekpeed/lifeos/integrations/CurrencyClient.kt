package com.alekpeed.lifeos.integrations

import com.alekpeed.lifeos.Storage
import com.alekpeed.lifeos.net.httpGet
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// open.er-api.com — keyless live exchange rates, base USD (~160 currencies).
// Rates persist to Storage so conversion works offline; they refresh
// automatically once stale (6h) and on demand. The full ISO-4217 directory below
// backs the type-ahead picker: typing "j" surfaces Jordan/Japan/Jamaica…,
// refining to "jpy" pins the Japanese Yen — matches run against code, currency
// name, and country/region alike.
object CurrencyClient {

    private val json = Json { ignoreUnknownKeys = true }

    private const val CACHE_KEY = "CurrencyRatesCache"
    private const val STALE_SECONDS = 6 * 60 * 60

    @Serializable
    private data class RatesCache(val fetchedAt: Long = 0, val rates: Map<String, Double> = emptyMap())

    private var cache: RatesCache? = null

    private fun loaded(): RatesCache {
        cache?.let { return it }
        val fromDisk = Storage.read(CACHE_KEY)?.let { raw ->
            runCatching { json.decodeFromString<RatesCache>(raw) }.getOrNull()
        } ?: RatesCache()
        cache = fromDisk
        return fromDisk
    }

    val common = listOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR", "BRL", "MXN", "KRW")

    fun hasRates(): Boolean = loaded().rates.isNotEmpty()

    // Epoch seconds of the last successful fetch (0 = never).
    fun ratesAgeEpochSeconds(): Long = loaded().fetchedAt

    fun isStale(): Boolean {
        val c = loaded()
        return c.rates.isEmpty() || Clock.System.now().epochSeconds - c.fetchedAt > STALE_SECONDS
    }

    // Fetch rates if missing or stale (or forced). Cached rates survive a failed
    // refresh — stale beats nothing when offline.
    suspend fun ensureRates(force: Boolean = false): Boolean {
        if (!force && !isStale()) return true
        return try {
            val res = httpGet("https://open.er-api.com/v6/latest/USD")
            if (!res.ok) return hasRates()
            val root = json.parseToJsonElement(res.body).jsonObject
            if (root["result"]?.jsonPrimitive?.content != "success") return hasRates()
            val rates = root["rates"]?.jsonObject ?: return hasRates()
            val parsed = rates.mapValues { it.value.jsonPrimitive.content.toDoubleOrNull() ?: 0.0 }
            if (parsed.isEmpty()) return hasRates()
            val next = RatesCache(Clock.System.now().epochSeconds, parsed)
            cache = next
            Storage.write(CACHE_KEY, json.encodeToString(next))
            true
        } catch (e: Exception) {
            hasRates()
        }
    }

    // Convert an amount between two currencies using the USD-based rates. Returns
    // null if rates aren't loaded or a currency is unknown.
    fun convert(amount: Double, from: String, to: String): Double? {
        val rates = loaded().rates
        val rFrom = rates[from.uppercase()] ?: return null
        val rTo = rates[to.uppercase()] ?: return null
        if (rFrom == 0.0) return null
        // amount(from) → USD → to
        return amount / rFrom * rTo
    }

    // ---- Directory + type-ahead search --------------------------------------

    data class CurrencyInfo(val code: String, val name: String, val region: String)

    // Rank: code prefix → name/region word prefix → contains-anywhere.
    fun search(query: String, limit: Int = 12): List<CurrencyInfo> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return directory.filter { it.code in common }.take(limit)
        fun rank(c: CurrencyInfo): Int {
            val code = c.code.lowercase()
            val name = c.name.lowercase()
            val region = c.region.lowercase()
            return when {
                code.startsWith(q) -> 0
                name.startsWith(q) || region.startsWith(q) -> 1
                name.split(' ').any { it.startsWith(q) } || region.split(' ', '/').any { it.startsWith(q) } -> 2
                code.contains(q) || name.contains(q) || region.contains(q) -> 3
                else -> -1
            }
        }
        return directory.mapNotNull { c -> rank(c).takeIf { it >= 0 }?.let { it to c } }
            .sortedWith(compareBy({ it.first }, { it.second.code }))
            .map { it.second }
            .take(limit)
    }

    fun info(code: String): CurrencyInfo? = directory.firstOrNull { it.code.equals(code, ignoreCase = true) }

    // Active ISO-4217 currencies (the set open.er-api serves). Region text is part
    // of the search surface, so country names find their currencies.
    val directory: List<CurrencyInfo> = listOf(
        CurrencyInfo("AED", "UAE Dirham", "United Arab Emirates"),
        CurrencyInfo("AFN", "Afghan Afghani", "Afghanistan"),
        CurrencyInfo("ALL", "Albanian Lek", "Albania"),
        CurrencyInfo("AMD", "Armenian Dram", "Armenia"),
        CurrencyInfo("ANG", "Netherlands Antillean Guilder", "Curacao / Sint Maarten"),
        CurrencyInfo("AOA", "Angolan Kwanza", "Angola"),
        CurrencyInfo("ARS", "Argentine Peso", "Argentina"),
        CurrencyInfo("AUD", "Australian Dollar", "Australia"),
        CurrencyInfo("AWG", "Aruban Florin", "Aruba"),
        CurrencyInfo("AZN", "Azerbaijani Manat", "Azerbaijan"),
        CurrencyInfo("BAM", "Convertible Mark", "Bosnia and Herzegovina"),
        CurrencyInfo("BBD", "Barbadian Dollar", "Barbados"),
        CurrencyInfo("BDT", "Bangladeshi Taka", "Bangladesh"),
        CurrencyInfo("BGN", "Bulgarian Lev", "Bulgaria"),
        CurrencyInfo("BHD", "Bahraini Dinar", "Bahrain"),
        CurrencyInfo("BIF", "Burundian Franc", "Burundi"),
        CurrencyInfo("BMD", "Bermudian Dollar", "Bermuda"),
        CurrencyInfo("BND", "Brunei Dollar", "Brunei"),
        CurrencyInfo("BOB", "Bolivian Boliviano", "Bolivia"),
        CurrencyInfo("BRL", "Brazilian Real", "Brazil"),
        CurrencyInfo("BSD", "Bahamian Dollar", "Bahamas"),
        CurrencyInfo("BTN", "Bhutanese Ngultrum", "Bhutan"),
        CurrencyInfo("BWP", "Botswana Pula", "Botswana"),
        CurrencyInfo("BYN", "Belarusian Ruble", "Belarus"),
        CurrencyInfo("BZD", "Belize Dollar", "Belize"),
        CurrencyInfo("CAD", "Canadian Dollar", "Canada"),
        CurrencyInfo("CDF", "Congolese Franc", "DR Congo"),
        CurrencyInfo("CHF", "Swiss Franc", "Switzerland / Liechtenstein"),
        CurrencyInfo("CLP", "Chilean Peso", "Chile"),
        CurrencyInfo("CNY", "Chinese Yuan", "China"),
        CurrencyInfo("COP", "Colombian Peso", "Colombia"),
        CurrencyInfo("CRC", "Costa Rican Colon", "Costa Rica"),
        CurrencyInfo("CUP", "Cuban Peso", "Cuba"),
        CurrencyInfo("CVE", "Cape Verdean Escudo", "Cape Verde"),
        CurrencyInfo("CZK", "Czech Koruna", "Czech Republic / Czechia"),
        CurrencyInfo("DJF", "Djiboutian Franc", "Djibouti"),
        CurrencyInfo("DKK", "Danish Krone", "Denmark"),
        CurrencyInfo("DOP", "Dominican Peso", "Dominican Republic"),
        CurrencyInfo("DZD", "Algerian Dinar", "Algeria"),
        CurrencyInfo("EGP", "Egyptian Pound", "Egypt"),
        CurrencyInfo("ERN", "Eritrean Nakfa", "Eritrea"),
        CurrencyInfo("ETB", "Ethiopian Birr", "Ethiopia"),
        CurrencyInfo("EUR", "Euro", "Eurozone / Europe"),
        CurrencyInfo("FJD", "Fijian Dollar", "Fiji"),
        CurrencyInfo("FKP", "Falkland Islands Pound", "Falkland Islands"),
        CurrencyInfo("GBP", "British Pound Sterling", "United Kingdom / England"),
        CurrencyInfo("GEL", "Georgian Lari", "Georgia"),
        CurrencyInfo("GGP", "Guernsey Pound", "Guernsey"),
        CurrencyInfo("GHS", "Ghanaian Cedi", "Ghana"),
        CurrencyInfo("GIP", "Gibraltar Pound", "Gibraltar"),
        CurrencyInfo("GMD", "Gambian Dalasi", "Gambia"),
        CurrencyInfo("GNF", "Guinean Franc", "Guinea"),
        CurrencyInfo("GTQ", "Guatemalan Quetzal", "Guatemala"),
        CurrencyInfo("GYD", "Guyanese Dollar", "Guyana"),
        CurrencyInfo("HKD", "Hong Kong Dollar", "Hong Kong"),
        CurrencyInfo("HNL", "Honduran Lempira", "Honduras"),
        CurrencyInfo("HRK", "Croatian Kuna", "Croatia"),
        CurrencyInfo("HTG", "Haitian Gourde", "Haiti"),
        CurrencyInfo("HUF", "Hungarian Forint", "Hungary"),
        CurrencyInfo("IDR", "Indonesian Rupiah", "Indonesia"),
        CurrencyInfo("ILS", "Israeli New Shekel", "Israel"),
        CurrencyInfo("IMP", "Isle of Man Pound", "Isle of Man"),
        CurrencyInfo("INR", "Indian Rupee", "India"),
        CurrencyInfo("IQD", "Iraqi Dinar", "Iraq"),
        CurrencyInfo("IRR", "Iranian Rial", "Iran"),
        CurrencyInfo("ISK", "Icelandic Krona", "Iceland"),
        CurrencyInfo("JEP", "Jersey Pound", "Jersey"),
        CurrencyInfo("JMD", "Jamaican Dollar", "Jamaica"),
        CurrencyInfo("JOD", "Jordanian Dinar", "Jordan"),
        CurrencyInfo("JPY", "Japanese Yen", "Japan"),
        CurrencyInfo("KES", "Kenyan Shilling", "Kenya"),
        CurrencyInfo("KGS", "Kyrgyzstani Som", "Kyrgyzstan"),
        CurrencyInfo("KHR", "Cambodian Riel", "Cambodia"),
        CurrencyInfo("KID", "Kiribati Dollar", "Kiribati"),
        CurrencyInfo("KMF", "Comorian Franc", "Comoros"),
        CurrencyInfo("KRW", "South Korean Won", "South Korea"),
        CurrencyInfo("KWD", "Kuwaiti Dinar", "Kuwait"),
        CurrencyInfo("KYD", "Cayman Islands Dollar", "Cayman Islands"),
        CurrencyInfo("KZT", "Kazakhstani Tenge", "Kazakhstan"),
        CurrencyInfo("LAK", "Lao Kip", "Laos"),
        CurrencyInfo("LBP", "Lebanese Pound", "Lebanon"),
        CurrencyInfo("LKR", "Sri Lankan Rupee", "Sri Lanka"),
        CurrencyInfo("LRD", "Liberian Dollar", "Liberia"),
        CurrencyInfo("LSL", "Lesotho Loti", "Lesotho"),
        CurrencyInfo("LYD", "Libyan Dinar", "Libya"),
        CurrencyInfo("MAD", "Moroccan Dirham", "Morocco"),
        CurrencyInfo("MDL", "Moldovan Leu", "Moldova"),
        CurrencyInfo("MGA", "Malagasy Ariary", "Madagascar"),
        CurrencyInfo("MKD", "Macedonian Denar", "North Macedonia"),
        CurrencyInfo("MMK", "Myanmar Kyat", "Myanmar / Burma"),
        CurrencyInfo("MNT", "Mongolian Tugrik", "Mongolia"),
        CurrencyInfo("MOP", "Macanese Pataca", "Macau"),
        CurrencyInfo("MRU", "Mauritanian Ouguiya", "Mauritania"),
        CurrencyInfo("MUR", "Mauritian Rupee", "Mauritius"),
        CurrencyInfo("MVR", "Maldivian Rufiyaa", "Maldives"),
        CurrencyInfo("MWK", "Malawian Kwacha", "Malawi"),
        CurrencyInfo("MXN", "Mexican Peso", "Mexico"),
        CurrencyInfo("MYR", "Malaysian Ringgit", "Malaysia"),
        CurrencyInfo("MZN", "Mozambican Metical", "Mozambique"),
        CurrencyInfo("NAD", "Namibian Dollar", "Namibia"),
        CurrencyInfo("NGN", "Nigerian Naira", "Nigeria"),
        CurrencyInfo("NIO", "Nicaraguan Cordoba", "Nicaragua"),
        CurrencyInfo("NOK", "Norwegian Krone", "Norway"),
        CurrencyInfo("NPR", "Nepalese Rupee", "Nepal"),
        CurrencyInfo("NZD", "New Zealand Dollar", "New Zealand"),
        CurrencyInfo("OMR", "Omani Rial", "Oman"),
        CurrencyInfo("PAB", "Panamanian Balboa", "Panama"),
        CurrencyInfo("PEN", "Peruvian Sol", "Peru"),
        CurrencyInfo("PGK", "Papua New Guinean Kina", "Papua New Guinea"),
        CurrencyInfo("PHP", "Philippine Peso", "Philippines"),
        CurrencyInfo("PKR", "Pakistani Rupee", "Pakistan"),
        CurrencyInfo("PLN", "Polish Zloty", "Poland"),
        CurrencyInfo("PYG", "Paraguayan Guarani", "Paraguay"),
        CurrencyInfo("QAR", "Qatari Riyal", "Qatar"),
        CurrencyInfo("RON", "Romanian Leu", "Romania"),
        CurrencyInfo("RSD", "Serbian Dinar", "Serbia"),
        CurrencyInfo("RUB", "Russian Ruble", "Russia"),
        CurrencyInfo("RWF", "Rwandan Franc", "Rwanda"),
        CurrencyInfo("SAR", "Saudi Riyal", "Saudi Arabia"),
        CurrencyInfo("SBD", "Solomon Islands Dollar", "Solomon Islands"),
        CurrencyInfo("SCR", "Seychellois Rupee", "Seychelles"),
        CurrencyInfo("SDG", "Sudanese Pound", "Sudan"),
        CurrencyInfo("SEK", "Swedish Krona", "Sweden"),
        CurrencyInfo("SGD", "Singapore Dollar", "Singapore"),
        CurrencyInfo("SHP", "Saint Helena Pound", "Saint Helena"),
        CurrencyInfo("SLE", "Sierra Leonean Leone", "Sierra Leone"),
        CurrencyInfo("SOS", "Somali Shilling", "Somalia"),
        CurrencyInfo("SRD", "Surinamese Dollar", "Suriname"),
        CurrencyInfo("SSP", "South Sudanese Pound", "South Sudan"),
        CurrencyInfo("STN", "Sao Tome Dobra", "Sao Tome and Principe"),
        CurrencyInfo("SYP", "Syrian Pound", "Syria"),
        CurrencyInfo("SZL", "Swazi Lilangeni", "Eswatini / Swaziland"),
        CurrencyInfo("THB", "Thai Baht", "Thailand"),
        CurrencyInfo("TJS", "Tajikistani Somoni", "Tajikistan"),
        CurrencyInfo("TMT", "Turkmenistani Manat", "Turkmenistan"),
        CurrencyInfo("TND", "Tunisian Dinar", "Tunisia"),
        CurrencyInfo("TOP", "Tongan Pa'anga", "Tonga"),
        CurrencyInfo("TRY", "Turkish Lira", "Turkey"),
        CurrencyInfo("TTD", "Trinidad and Tobago Dollar", "Trinidad and Tobago"),
        CurrencyInfo("TVD", "Tuvaluan Dollar", "Tuvalu"),
        CurrencyInfo("TWD", "New Taiwan Dollar", "Taiwan"),
        CurrencyInfo("TZS", "Tanzanian Shilling", "Tanzania"),
        CurrencyInfo("UAH", "Ukrainian Hryvnia", "Ukraine"),
        CurrencyInfo("UGX", "Ugandan Shilling", "Uganda"),
        CurrencyInfo("USD", "US Dollar", "United States / America"),
        CurrencyInfo("UYU", "Uruguayan Peso", "Uruguay"),
        CurrencyInfo("UZS", "Uzbekistani Som", "Uzbekistan"),
        CurrencyInfo("VES", "Venezuelan Bolivar", "Venezuela"),
        CurrencyInfo("VND", "Vietnamese Dong", "Vietnam"),
        CurrencyInfo("VUV", "Vanuatu Vatu", "Vanuatu"),
        CurrencyInfo("WST", "Samoan Tala", "Samoa"),
        CurrencyInfo("XAF", "Central African CFA Franc", "Cameroon / Chad / Gabon / Central Africa"),
        CurrencyInfo("XCD", "East Caribbean Dollar", "Grenada / Saint Lucia / Caribbean"),
        CurrencyInfo("XDR", "Special Drawing Rights", "IMF"),
        CurrencyInfo("XOF", "West African CFA Franc", "Senegal / Ivory Coast / Mali / West Africa"),
        CurrencyInfo("XPF", "CFP Franc", "French Polynesia / New Caledonia"),
        CurrencyInfo("YER", "Yemeni Rial", "Yemen"),
        CurrencyInfo("ZAR", "South African Rand", "South Africa"),
        CurrencyInfo("ZMW", "Zambian Kwacha", "Zambia"),
        CurrencyInfo("ZWL", "Zimbabwean Dollar", "Zimbabwe"),
    )
}
