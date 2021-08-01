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
import java.io.FileWriter
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    readActors()
    readEvents()
    //writeGeojson()
}

private fun configureObjectMapper() {
    objectMapper.registerModule(JavaTimeModule())
    val module = SimpleModule()
    module.addDeserializer(LocalDateTime::class.java, CustomDateTimeDeserializer())
    objectMapper.registerModule(module)
}

fun readEvents() {

    val converter =
        ResourceConverter(objectMapper, Event::class.java, Actor::class.java, District::class.java, Image::class.java);
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
    val features = eventsInGeoAndTimeRange.map { eventToGeoJsonFeature(it) }
    val content = featureCollection(features)
    val root = objectMapper.readTree(content)
    val file = File("${outputPath}/kieznotiz-events.geojson")
    //FileWriter("D:/kieznotiz-events.geojson").use { it.write(content) }
    objectMapper.writeValue(file, root)
    println(""""${file.absolutePath} written""")

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
    val actorsInGeoRange = actors.filter { isInLocationBounds(it.geodata) }
    println("Actors in Neustadt-Neuschönefeld and Volkmarsdorf: ${actorsInGeoRange.size}")

    val features = actorsInGeoRange.map { actorToGeoJsonFeature(it) }
    val content = featureCollection(features)
    val root = objectMapper.readTree(content)
    val file = File("${outputPath}/kieznotiz.geojson")
    //FileWriter("D:/kieznotiz.geojson").use { it.write(content) }
    objectMapper.writeValue(file, root)
    println(""""${file.absolutePath} written""")

    //writeStatistics(actors)
}

fun writeStatistics(actors: List<Actor>) {
    val activeValues = mutableMapOf<Boolean?, Long>()
    val titleValues = mutableMapOf<String?, Long>()
    val createdValues = mutableMapOf<LocalDateTime?, Long>()
    val changedValues = mutableMapOf<LocalDateTime?, Long>()
    val pathValues = mutableMapOf<String?, Long>()
    val addressValues = mutableMapOf<String?, Long>()
    val geodataValues = mutableMapOf<String?, Long>()
    val contactPersonValues = mutableMapOf<String?, Long>()
    val contactPersonFunctionValues = mutableMapOf<String?, Long>()
    val descriptionValues = mutableMapOf<String?, Long>()
    val contactEmailValues = mutableMapOf<String?, Long>()
    val barrierFreeLocationValues = mutableMapOf<Boolean?, Long>()
    val openingTimesValues = mutableMapOf<String?, Long>()
    val contactPhoneValues = mutableMapOf<String?, Long>()
    val externalUrlValues = mutableMapOf<String?, Long>()

    val pathAlias = mutableMapOf<String?, Long>()
    val pathPid = mutableMapOf<Long?, Long>()
    val pathLangcode = mutableMapOf<String?, Long>()

    val addressLangcode = mutableMapOf<String?, Long>()
    val addressCountryCode = mutableMapOf<String?, Long>()
    val addressAdministrativeArea = mutableMapOf<String?, Long>()
    val addressLocality = mutableMapOf<String?, Long>()
    val addressDependentLocality = mutableMapOf<String?, Long>()
    val addressPostalCode = mutableMapOf<String?, Long>()
    val addressSortingCode = mutableMapOf<String?, Long>()
    val addressAddressLine1 = mutableMapOf<String?, Long>()
    val addressAddressLine2 = mutableMapOf<String?, Long>()
    val addressOrganization = mutableMapOf<String?, Long>()
    val addressGivenName = mutableMapOf<String?, Long>()
    val addressAdditionalName = mutableMapOf<String?, Long>()
    val addressFamilyName = mutableMapOf<String?, Long>()

    val geodatavalue = mutableMapOf<String?, Long>()
    val geodatageoType = mutableMapOf<String?, Long>()
    val geodatalat = mutableMapOf<Float?, Long>()
    val geodatalon = mutableMapOf<Float?, Long>()
    val geodataleft = mutableMapOf<Float?, Long>()
    val geodatatop = mutableMapOf<Float?, Long>()
    val geodataright = mutableMapOf<Float?, Long>()
    val geodatabottom = mutableMapOf<Float?, Long>()
    val geodatageohash = mutableMapOf<String?, Long>()
    val geodatalatlon = mutableMapOf<String?, Long>()

    val descriptionvalue = mutableMapOf<String?, Long>()
    val descriptionformat = mutableMapOf<String?, Long>()
    val descriptionprocessed = mutableMapOf<String?, Long>()

    val externalurluri = mutableMapOf<String?, Long>()
    val externalurltitle = mutableMapOf<String?, Long>()
    val externalurloptions = mutableMapOf<String?, Long>()

    val imageid = mutableMapOf<String?, Long>()
    val imagemeta = mutableMapOf<String?, Long>()
    val imagedrupalInternalFid = mutableMapOf<Long?, Long>()
    val imagefilename = mutableMapOf<String?, Long>()
    val imageuri = mutableMapOf<String?, Long>()
    val imagefilemime = mutableMapOf<String?, Long>()
    val imagefilesize = mutableMapOf<Long?, Long>()

    val imageMetaDataalt = mutableMapOf<String?, Long>()
    val imageMetaDatatitle = mutableMapOf<String?, Long>()
    val imageMetaDatawidth = mutableMapOf<Int?, Long>()
    val imageMetaDataheight = mutableMapOf<Int?, Long>()

    val urivalue = mutableMapOf<String?, Long>()
    val uriurl = mutableMapOf<String?, Long>()

    val district = mutableMapOf<String?, Long>()
    val targetGroup = mutableMapOf<String?, Long>()
    val tag = mutableMapOf<String?, Long>()
    val category = mutableMapOf<String?, Long>()
    val categoryParent = mutableMapOf<String?, Long>()
    val actorType = mutableMapOf<String?, Long>()

    for (actor in actors) {
        countValueOccurence(activeValues, actor.active)
        countValueOccurence(titleValues, actor.title)
        countValueOccurence(createdValues, actor.created)
        countValueOccurence(changedValues, actor.changed)
        countValueOccurence(pathValues, actor.path?.toString())
        countValueOccurence(addressValues, actor.address?.toString())
        countValueOccurence(geodataValues, actor.geodata?.toString())
        countValueOccurence(contactPersonValues, actor.contactPerson)
        countValueOccurence(contactPersonFunctionValues, actor.contactPersonFunction)
        countValueOccurence(descriptionValues, actor.description.toString())
        countValueOccurence(contactEmailValues, actor.contactEmail)
        countValueOccurence(barrierFreeLocationValues, actor.barrierFreeLocation)
        countValueOccurence(openingTimesValues, actor.openingTimes)
        countValueOccurence(contactPhoneValues, actor.contactPhone)
        countValueOccurence(externalUrlValues, actor.externalUrl?.toString())

        countValueOccurence(pathAlias, actor.path?.alias)
        countValueOccurence(pathPid, actor.path?.pid)
        countValueOccurence(pathLangcode, actor.path?.langcode)

        countValueOccurence(addressLangcode, actor.address?.langcode)
        countValueOccurence(addressCountryCode, actor.address?.countryCode)
        countValueOccurence(addressAdministrativeArea, actor.address?.administrativeArea)
        countValueOccurence(addressLocality, actor.address?.locality?.toString())
        countValueOccurence(addressDependentLocality, actor.address?.dependentLocality)
        countValueOccurence(addressPostalCode, actor.address?.postalCode)
        countValueOccurence(addressSortingCode, actor.address?.sortingCode)
        countValueOccurence(addressAddressLine1, actor.address?.addressLine1)
        countValueOccurence(addressAddressLine2, actor.address?.addressLine2)
        countValueOccurence(addressOrganization, actor.address?.organization)
        countValueOccurence(addressGivenName, actor.address?.givenName)
        countValueOccurence(addressAdditionalName, actor.address?.additionalName)
        countValueOccurence(addressFamilyName, actor.address?.familyName)

        countValueOccurence(geodatavalue, actor.geodata?.value)
        countValueOccurence(geodatageoType, actor.geodata?.geoType)
        countValueOccurence(geodatalat, actor.geodata?.lat)
        countValueOccurence(geodatalon, actor.geodata?.lon)
        countValueOccurence(geodataleft, actor.geodata?.left)
        countValueOccurence(geodatatop, actor.geodata?.top)
        countValueOccurence(geodataright, actor.geodata?.right)
        countValueOccurence(geodatabottom, actor.geodata?.bottom)
        countValueOccurence(geodatageohash, actor.geodata?.geohash)
        countValueOccurence(geodatalatlon, actor.geodata?.latlon)

        countValueOccurence(descriptionvalue, actor.description?.value)
        countValueOccurence(descriptionformat, actor.description?.format?.toString())
        countValueOccurence(descriptionprocessed, actor.description?.processed)

        countValueOccurence(externalurluri, actor.externalUrl?.uri)
        countValueOccurence(externalurltitle, actor.externalUrl?.title)
        countValueOccurence(externalurloptions, actor.externalUrl?.options?.joinToString(", "))

        countValueOccurence(imageid, actor.image?.id)
        countValueOccurence(imagemeta, actor.image?.meta?.toString())
        countValueOccurence(imagedrupalInternalFid, actor.image?.drupalInternalFid)
        countValueOccurence(imagefilename, actor.image?.filename)
        countValueOccurence(imageuri, actor.image?.uri?.toString())
        countValueOccurence(imagefilemime, actor.image?.filemime?.toString())
        countValueOccurence(imagefilesize, actor.image?.filesize)

        countValueOccurence(imageMetaDataalt, actor.image?.meta?.alt)
        countValueOccurence(imageMetaDatatitle, actor.image?.meta?.title)
        countValueOccurence(imageMetaDatawidth, actor.image?.meta?.width)
        countValueOccurence(imageMetaDataheight, actor.image?.meta?.height)

        countValueOccurence(urivalue, actor.image?.uri?.value)
        countValueOccurence(uriurl, actor.image?.uri?.url)

        countValueOccurence(district, actor.district?.name)
        actor.targetGroups?.forEach { countValueOccurence(targetGroup, it.name) }
        actor.tags?.forEach { countValueOccurence(tag, it.name) }
        actor.categories?.forEach { countValueOccurence(category, it.name) }
        actor.categories?.forEach { countValueOccurence(categoryParent, it.parent?.name) }
        countValueOccurence(actorType, actor.type?.id)
    }

    FileWriter(File("D:/statistics.txt")).use {
        it.write(
            """
                activeValues: $activeValues
                titleValues: $titleValues
                createdValues: $createdValues
                changedValues: $changedValues
                pathValues: $pathValues 
                addressValues: $addressValues 
                geodataValues: $geodataValues 
                contactPersonValues: $contactPersonValues 
                contactPersonFunctionValues: $contactPersonFunctionValues 
                descriptionValues: $descriptionValues 
                contactEmailValues: $contactEmailValues 
                barrierFreeLocationValues = $barrierFreeLocationValues
                openingTimesValues: $openingTimesValues 
                contactPhoneValues: $contactPhoneValues 
                externalUrlValues: $externalUrlValues 

                pathAlias: $pathAlias 
                pathPid = $pathPid
                pathLangcode: $pathLangcode 

                addressLangcode: $addressLangcode 
                addressCountryCode: $addressCountryCode 
                addressAdministrativeArea: $addressAdministrativeArea 
                addressLocality: $addressLocality 
                addressDependentLocality: $addressDependentLocality 
                addressPostalCode: $addressPostalCode 
                addressSortingCode: $addressSortingCode 
                addressAddressLine1: $addressAddressLine1 
                addressAddressLine2: $addressAddressLine2 
                addressOrganization: $addressOrganization 
                addressGivenName: $addressGivenName 
                addressAdditionalName: $addressAdditionalName 
                addressFamilyName: $addressFamilyName 

                geodatavalue: $geodatavalue 
                geodatageoType: $geodatageoType 
                geodatalat: $geodatalat
                geodatalon: $geodatalon
                geodataleft: $geodataleft
                geodatatop: $geodatatop
                geodataright: $geodataright
                geodatabottom: $geodatabottom
                geodatageohash: $geodatageohash
                geodatalatlon: $geodatalatlon

                descriptionvalue: $descriptionvalue
                descriptionformat: $descriptionformat
                descriptionprocessed: $descriptionprocessed

                externalurluri: $externalurluri
                externalurltitle: $externalurltitle
                externalurloptions: $externalurloptions

                imageid: $imageid
                imagemeta: $imagemeta
                imagedrupalInternalFid: $imagedrupalInternalFid
                imagefilename: $imagefilename
                imageuri: $imageuri
                imagefilemime: $imagefilemime
                imagefilesize: $imagefilesize

                imageMetaDataalt: $imageMetaDataalt
                imageMetaDatatitle: $imageMetaDatatitle
                imageMetaDatawidth: $imageMetaDatawidth
                imageMetaDataheight: $imageMetaDataheight

                urivalue: $urivalue
                uriurl: $uriurl

                district: $district
                targetGroup: $targetGroup
                tag: $tag
                category: $category 
                categoryParent: $categoryParent
                actorType: $actorType
        """.trimIndent()
        )
    }
}

fun <T> countValueOccurence(values: MutableMap<T?, Long>, value: T?) {
    if (values.containsKey(value)) {
        values[value] = values[value]!! + 1
    } else {
        values[value] = 1
    }
}

fun actorToGeoJsonFeature(actor: Actor): String = """{
    "type": "Feature",
    "properties": {
        "title": ${actor.title?.let { sanitize(it) }},
        "description": ${actor.description?.processed?.let { sanitize(it) }},
        "address1": ${actor.address?.addressLine1?.let { sanitize(it) }},
        "address2": ${actor.address?.addressLine2?.let { sanitize(it) }},
        "url": ${actor.externalUrl?.uri?.let { sanitize(it) }},
        "contact": ${actor.contactPerson?.let { sanitize(it) }},
        "email": ${actor.contactEmail?.let { sanitize(it) }},
        "openingTimes": ${actor.openingTimes?.let { sanitize(it) }}    
    },
    "geometry": {
        "type": "Point",
        "coordinates": [${actor.geodata?.lon}, ${actor.geodata?.lat}]
    },
    "id": ${actor.id?.let { sanitize(it) }}    
   
}""".trimIndent()

val dateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

fun eventToGeoJsonFeature(event: Event): String = """{
    "type": "Feature",
    "properties": {
        "title": ${event.title?.let { sanitize(it) }},
        "description": ${event.description?.processed?.let { sanitize(it) }},
        "address1": ${event.address?.addressLine1?.let { sanitize(it) }},
        "address2": ${event.address?.addressLine2?.let { sanitize(it) }},
        "actor": ${event.actor?.title?.let { sanitize(it) }},
        "url": ${event.externalWebsite?.uri?.let { sanitize(it) }},
        "start": ${
    event.occurrences.filter {
        it.startDate.isAfter(LocalDateTime.now()) || (it.endDate == null || it.endDate!!.isAfter(
            LocalDateTime.now()
        ))
    }.let { if (it.isNotEmpty()) it[0].startDate else null }?.let { "\"${dateTimeFormatter.format(it)}\"" }
},
        "end": ${
    event.occurrences.filter {
        it.startDate.isAfter(LocalDateTime.now()) || (it.endDate == null || it.endDate!!.isAfter(
            LocalDateTime.now()
        ))
    }.let { if (it.isNotEmpty()) it[0].endDate else null }?.let { "\"${dateTimeFormatter.format(it)}\"" }
}
            },
    "geometry": {
        "type": "Point",
        "coordinates": [${event.geodata?.lon}, ${event.geodata?.lat}]
    },
    "id": ${event.id?.let { sanitize(it) }}    
   
}""".trimIndent()

fun sanitize(str: String) =
    if (str.isBlank()) null else "\"${str.replace("\"", "\\\"").replace("\n", " ").replace("\t", " ")}\""

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