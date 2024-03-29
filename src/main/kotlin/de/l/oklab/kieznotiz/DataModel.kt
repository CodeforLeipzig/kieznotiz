package de.l.oklab.kieznotiz

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.jasminb.jsonapi.RelType
import com.github.jasminb.jsonapi.annotations.Id
import com.github.jasminb.jsonapi.annotations.Meta
import com.github.jasminb.jsonapi.annotations.Relationship
import com.github.jasminb.jsonapi.annotations.Type
import java.time.LocalDateTime

open class BaseModel(@Id var id: String?)

open class Element(
    id: String?,
    @JsonProperty("drupal_internal__nid") var drupalInternalNid: Long,
    @JsonProperty("drupal_internal__vid") var drupalInternalVid: Long,
    @JsonProperty("active") var active: Boolean?,
    @JsonProperty("title") var title: String?,
    @JsonProperty("created") var created: LocalDateTime?,
    @JsonProperty("changed") var changed: LocalDateTime?,
    @JsonProperty("path") var path: Path?,
    @JsonProperty("address") var address: Address?,
    @JsonProperty("geodata") var geodata: GeoData?,
    @JsonProperty("description") var description: Description?,
    @JsonProperty("barrier_free_location") var barrierFreeLocation: Boolean?,
    @JsonProperty("published_at") var publishedAt: LocalDateTime?
) : BaseModel(id) {

    @Relationship("district")
    var district: District? = null

    @Relationship("image")
    var image: Image? = null

    @Relationship("targetGroups")
    var targetGroups: List<TargetGroup>? = null

    @Relationship("tags")
    var tags: List<Tag>? = null

    @Relationship("categories")
    var categories: List<Category>? = null
}

@Type("akteur")
class Actor(
    @JsonProperty("id") id: String?,
    @JsonProperty("drupal_internal__nid") drupalInternalNid: Long,
    @JsonProperty("drupal_internal__vid") drupalInternalVid: Long,
    @JsonProperty("active") active: Boolean?,
    @JsonProperty("title") title: String?,
    @JsonProperty("created") created: LocalDateTime?,
    @JsonProperty("changed") changed: LocalDateTime?,
    @JsonProperty("path") path: Path?,
    @JsonProperty("address") address: Address?,
    @JsonProperty("geodata") geodata: GeoData?,
    @JsonProperty("published_at") publishedAt: LocalDateTime?,
    @JsonProperty("contact_person") var contactPerson: String?,
    @JsonProperty("contact_person_function") var contactPersonFunction: String?,
    @JsonProperty("description") description: Description?,
    @JsonProperty("contact_email") var contactEmail: String?,
    @JsonProperty("barrier_free_location") barrierFreeLocation: Boolean?,
    @JsonProperty("opening_times") var openingTimes: String?,
    @JsonProperty("contact_phone") var contactPhone: String?,
    @JsonProperty("external_url") var externalUrl: ExternalUrl?,
) : Element(
    id,
    drupalInternalNid,
    drupalInternalVid,
    active,
    title,
    created,
    changed,
    path,
    address,
    geodata,
    description,
    barrierFreeLocation,
    publishedAt
) {

    @Relationship("typ")
    var type: ActorType? = null
}

@Type("event")
class Event(
    @JsonProperty("id") id: String?,
    @JsonProperty("drupal_internal__nid") drupalInternalNid: Long,
    @JsonProperty("drupal_internal__vid") drupalInternalVid: Long,
    @JsonProperty("active") active: Boolean,
    @JsonProperty("title") title: String,
    @JsonProperty("created") created: LocalDateTime,
    @JsonProperty("changed") changed: LocalDateTime,
    @JsonProperty("path") path: Path?,
    @JsonProperty("address") address: Address,
    @JsonProperty("geodata") geodata: GeoData,
    @JsonProperty("description") description: Description?,
    @JsonProperty("barrier_free_location") barrierFreeLocation: Boolean,
    @JsonProperty("published_at") publishedAt: LocalDateTime?,
    @JsonProperty("occurrences") var occurrences: List<Occurrence>,
    @JsonProperty("external_website") var externalWebsite: ExternalUrl?,
    @JsonProperty("field_is_featured") var fieldIsFeatured: Boolean,
    @JsonProperty("field_online_event") var fieldOnlineEvent: Boolean,
) : Element(
    id,
    drupalInternalNid,
    drupalInternalVid,
    active,
    title,
    created,
    changed,
    path,
    address,
    geodata,
    description,
    barrierFreeLocation,
    publishedAt
) {

    @Relationship(value="akteur", resolve = true, relType = RelType.RELATED)
    var actor: Actor? = null
}

data class Path(
    @JsonProperty("alias") var alias: String,
    @JsonProperty("pid") var pid: Long,
    @JsonProperty("langcode") var langcode: String
)

class Address(
    @JsonProperty("langcode") var langcode: String?/*LangCode?*/,
    @JsonProperty("country_code") var countryCode: String?/*CountryCode*/,
    @JsonProperty("administrative_area") var administrativeArea: String?,
    @JsonProperty("locality") var locality: String,
    @JsonProperty("dependent_locality") var dependentLocality: String?,
    @JsonProperty("postal_code") var postalCode: String,
    @JsonProperty("sorting_code") var sortingCode: String?,
    @JsonProperty("address_line1") var addressLine1: String,
    @JsonProperty("address_line2") var addressLine2: String?,
    @JsonProperty("organization") var organization: String?,
    @JsonProperty("given_name") var givenName: String?,
    @JsonProperty("additional_name") var additionalName: String?,
    @JsonProperty("family_name") var familyName: String?
)

enum class LangCode { de }
enum class CountryCode { DE }

class GeoData(
    @JsonProperty("value") var value: String,
    @JsonProperty("geo_type") var geoType: String/*GeoType*/,
    @JsonProperty("lat") var lat: Float,
    @JsonProperty("lon") var lon: Float,
    @JsonProperty("left") var left: Float,
    @JsonProperty("top") var top: Float,
    @JsonProperty("right") var right: Float,
    @JsonProperty("bottom") var bottom: Float,
    @JsonProperty("geohash") var geohash: String,
    @JsonProperty("latlon") var latlon: String
)

enum class GeoType { POINT }

class Description(
    @JsonProperty("value") var value: String,
    @JsonProperty("format") var format: DescFormat,
    @JsonProperty("processed") var processed: String
)

enum class DescFormat { basic_html, full_html }

class ExternalUrl(
    @JsonProperty("uri") var uri: String,
    @JsonProperty("title") var title: String?,
    @JsonProperty("options") var options: List<String>
)

@Type("bezirk")
class District(
    @JsonProperty("id") id: String?,
    @JsonProperty("name") var name: String?,
    //@Relationship("region") var region: Region?
) : BaseModel(id)

class Region(@JsonProperty("id") id: String) : BaseModel(id)

@Type("akteur_typ")
class ActorType(@JsonProperty("id") id: String?) : BaseModel(id)

@Type("file")
class Image(
    @JsonProperty("id") id: String?,
    @JsonProperty("meta") @Meta var meta: ImageMetaData?,
    @JsonProperty("drupal_internal__fid") var drupalInternalFid: Long,
    @JsonProperty("filename") var filename: String?,
    @JsonProperty("uri") var uri: Uri?,
    @JsonProperty("filemime") var filemime: FileMime?,
    @JsonProperty("filesize") var filesize: Long?
) : BaseModel(id)

class ImageMetaData(
    @JsonProperty("alt") var alt: String,
    @JsonProperty("title") var title: String,
    @JsonProperty("width") var width: Int,
    @JsonProperty("height") var height: Int
)

class Uri(
    @JsonProperty("value") var value: String,
    @JsonProperty("url") var url: String
)

enum class FileMime(var value: String) {
    GIF("image/gif")
}

@Type("target_group")
class TargetGroup(
    @JsonProperty("id") id: String?,
    @JsonProperty("drupal_internal__tid") var drupalInternalTid: Long,
    @JsonProperty("name") var name: String?
) : BaseModel(id)

@Type("tag")
class Tag(
    @JsonProperty("id") id: String?,
    @JsonProperty("drupal_internal__tid") var drupalInternalTid: Long,
    @JsonProperty("name") var name: String?
) : BaseModel(id)

@Type("category")
class Category(
    @JsonProperty("id") id: String?,
    @JsonProperty("drupal_internal__tid") var drupalInternalTid: Long,
    @JsonProperty("name") var name: String?
) : BaseModel(id) {
    @Relationship("region")
    var parent: Category? = null
}

class Occurrence(
    @JsonProperty("value") var startDate: LocalDateTime,
    @JsonProperty("end_value") var endDate: LocalDateTime?,
    @JsonProperty("rrule") var rrule: String?,
    @JsonProperty("timezone") var timezone: String?,
    @JsonProperty("infinite") var infinite: Boolean
)