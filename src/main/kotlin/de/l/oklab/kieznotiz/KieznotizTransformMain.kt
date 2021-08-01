package de.l.oklab.kieznotiz

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.jasminb.jsonapi.ResourceConverter
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val outputPath = "./docs"

private val minLat = 51.338207
private val maxLat = 51.349078
private val minLon = 12.392771
private val maxLon = 12.422404

private val objectMapper = jacksonObjectMapper()

class FeatureCollector(private val list: MutableList<String> = mutableListOf()) {

    fun collect(dataNode: ArrayNode) {
        val features = dataNode.map {
            val geodata = it.get("attributes").get("geodata")
            feature(it, geodata)
        }
        list.addAll(features)
    }

    private fun feature(node: JsonNode, geodata: JsonNode) = """
{
    "type": "Feature",
    "properties": ${objectMapper.writeValueAsString(node)},
    "geometry": {
        "type": "Point",
        "coordinates": [${geodata.get("lon")}, ${geodata.get("lat")}]
    },
    "id": "${node.get("id").asText()}"
}
""".trimMargin()

    fun getFeatures() = list.toList()
}

class JsonWalker(
    private val url: String, private val queryFragment: (Int) -> String,
    private val elemsPerPage: Int,
    private val collector: FeatureCollector = FeatureCollector()
) {

    fun walk(offset: Int = 0): List<String> {
        val downloadUrl = URL("${url}${queryFragment(offset)}")
        val rootNode = downloadUrl.openStream().use { objectMapper.readValue(it, JsonNode::class.java) }
        val dataNode = rootNode.get("data") as ArrayNode
        return if (!(dataNode.isNull || dataNode.size() == 0)) {
            collector.collect(dataNode)
            walk(offset + elemsPerPage)
        } else {
            collector.getFeatures()
        }
    }
}

class ElementCollector<T>(private val list: MutableList<T> = mutableListOf()) {

    fun collect(actors: List<T>) {
        list.addAll(actors)
    }

    fun getElements() = list.toList()
}

class ElementsWalker<T>(
    private val url: String, private val queryFragment: (Int) -> String,
    private val elemsPerPage: Int,
    private val converter: ResourceConverter,
    private val clazz: Class<T>,
    private val collector: ElementCollector<T> = ElementCollector()
) {

    fun walk(offset: Int = 0): List<T> {
        val downloadUrl = URL("${url}${queryFragment(offset)}")
        val elements = downloadUrl.openStream().use {
            val result = converter.readDocumentCollection(it, clazz)
            result.errors?.forEach { error -> println(error) }
            result.get()
        }
        return if (!(elements == null || elements.size == 0)) {
            collector.collect(elements)
            walk(offset + elemsPerPage)
        } else {
            collector.getElements()
        }
    }
}

fun main() {
    configureObjectMapper()
    //readEvents()
    //readActors()
    writeGeojson()
}

private fun configureObjectMapper() {
    objectMapper.registerModule(JavaTimeModule())
    val module = SimpleModule()
    module.addDeserializer(LocalDateTime::class.java, CustomDateTimeDeserializer())
    objectMapper.registerModule(module)
}

fun readEvents() {

    val converter = ResourceConverter(objectMapper, Event::class.java, Actor::class.java, District::class.java, Image::class.java);
    val elemsPerPage = 50
    val queryFragment = { offset: Int -> "?page%5Boffset%5D=${offset}&page%5Blimit%5D=${elemsPerPage}" }
    val eventsWalker = ElementsWalker(
        "https://leipziger-ecken.de/jsonapi/events",
        queryFragment,
        elemsPerPage,
        converter,
        Event::class.java
    )
    val events = eventsWalker.walk()
    println("Total events: ${events.size}")
    val eventsInGeoRange = events.filter { isInLocationBounds(it.geodata) }
    println("Events in Neustadt-Neuschönefeld and Volkmarsdorf: ${eventsInGeoRange.size}")
    val eventsInGeoAndTimeRange = eventsInGeoRange.filter { isInTimeRange(it) }
    println("Events in Neustadt-Neuschönefeld and Volkmarsdorf after today: ${eventsInGeoAndTimeRange.size}")
}

fun isInLocationBounds(geoData: GeoData?) =
    geoData != null && geoData.lat in minLat..maxLat && geoData.lon in minLon..maxLon

fun isInTimeRange(event: Event) =
    event.occurrences.filter { isTodayOrLater(it.startDate) || isTodayOrLater(it.endDate) }.isNotEmpty()

fun isTodayOrLater(date: LocalDateTime?): Boolean =
    date == null || date.isAfter(LocalDateTime.now().truncatedTo(ChronoUnit.DAYS))

fun readActors() {
    objectMapper.registerModule(JavaTimeModule());
    val converter = ResourceConverter(objectMapper, Actor::class.java, District::class.java, Image::class.java);
    val elemsPerPage = 50
    val queryFragment = { offset: Int -> "?page%5Boffset%5D=${offset}&page%5Blimit%5D=${elemsPerPage}" }
    val actorsWalker = ElementsWalker(
        "https://leipziger-ecken.de/jsonapi/akteure",
        queryFragment,
        elemsPerPage,
        converter,
        Actor::class.java
    )
    val actors = actorsWalker.walk()
    println("Total actors: ${actors.size}")
    val eventsInGeoRange = actors.filter { isInLocationBounds(it.geodata) }
    println("Actors in Neustadt-Neuschönefeld and Volkmarsdorf: ${eventsInGeoRange.size}")
}

fun writeGeojson() {
    val elemsPerPage = 50
    val queryFragment = { offset: Int -> "?page%5Boffset%5D=${offset}&page%5Blimit%5D=${elemsPerPage}" }
    val actorsWalker = JsonWalker("https://leipziger-ecken.de/jsonapi/akteure", queryFragment, elemsPerPage)
    val content = featureCollection(actorsWalker.walk())
    val root = objectMapper.readTree(content)
    val file = File("""$outputPath/kieznotiz.geojson""")
    //FileWriter(file).use { it.write(content) }

    objectMapper.writeValue(file, root)
    println(""""${file.absolutePath} written""")
}

fun featureCollection(features: List<String>): String {
    return """{
      "type": "FeatureCollection",
      "features": [
         ${features.joinToString(",")}
      ]
    }"""
}

class CustomDateTimeDeserializer : JsonDeserializer<LocalDateTime>() {
    @Throws(IOException::class, JsonProcessingException::class)

    override fun deserialize(jp: JsonParser, ctxt: DeserializationContext?): LocalDateTime {
        val node = jp.codec.readTree(jp) as TextNode
        val dateString: String = node.textValue()
        return LocalDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }
}