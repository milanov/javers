package org.javers.core.json.typeadapter

import groovy.json.JsonSlurper
import org.javers.core.diff.Change
import org.javers.core.diff.changetype.EntryAdded
import org.javers.core.diff.changetype.EntryChanged
import org.javers.core.diff.changetype.EntryRemoved
import org.javers.core.diff.changetype.NewObject
import org.javers.core.diff.changetype.ObjectRemoved
import org.javers.core.diff.changetype.ReferenceChange
import org.javers.core.diff.changetype.ValueChange
import org.javers.core.json.JsonConverter
import org.javers.core.model.DummyAddress
import org.javers.core.model.DummyNetworkAddress
import org.joda.time.LocalDateTime
import spock.lang.Specification
import spock.lang.Unroll

import static org.javers.core.json.JsonConverterBuilder.jsonConverter
import static org.javers.core.json.builder.ChangeTestBuilder.*
import static org.javers.core.json.builder.GlobalCdoIdTestBuilder.instanceId
import static org.javers.core.json.builder.GlobalCdoIdTestBuilder.unboundedValueObjectId
import static org.javers.core.model.DummyUserWithValues.dummyUserWithDate
import static org.javers.test.builder.DummyUserBuilder.dummyUser
import static org.javers.test.builder.DummyUserDetailsBuilder.dummyUserDetails

/**
 * @author bartosz walacik
 */
class JsonConverterDiffIntegrationTest extends Specification {
    class ClassWithChange{
        Change change
    }

    def "should be null safe when converting to json"(){
        given:
        JsonConverter jsonConverter = jsonConverter().build()

        when:
        String json = jsonConverter.toJson(new ClassWithChange())

        then:
        assert json.contains('"change": null')
    }

    def "should write property name, left & right values for ValueChange" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        ValueChange change = valueChange(dummyUser("kaz").build(),"flag",true,false)

        when:
        String jsonText = jsonConverter.toJson(change)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.property == "flag"
        json.leftValue == true
        json.rightValue == false
    }

    def "should write property name, key, left & right values for EntryChange" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        EntryChanged change = entryChange(dummyUser("kaz").build(),"valueMap","someKey", 1, 2)

        when:
        String jsonText = jsonConverter.toJson(change)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.property == "valueMap"
        json.key == "someKey"
        json.leftValue == 1
        json.rightValue == 2
    }

    @Unroll
    def "should write property name, key & value for #change.class.simpleName" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        String jsonText = jsonConverter.toJson(change)

        expect:
        def json = new JsonSlurper().parseText(jsonText)
        json.property == "valueMap"
        json.entry.key == "someKey"
        json.entry.value == 1

        where:
        change << [
                entryAdded(dummyUser("kaz").build(),"valueMap","someKey", 1),
                entryRemoved(dummyUser("kaz").build(),"valueMap","someKey", 1)
        ]
    }

    def "should write property name, leftId & rightId for ReferenceChange" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        ReferenceChange change = referenceChanged(dummyUser().build(),
                                                   "dummyUserDetails",
                                                   dummyUserDetails(1).build(),
                                                   dummyUserDetails(2).build())

        when:
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.property == "dummyUserDetails"
        json.leftReference.cdoId == 1
        json.leftReference.entity == "org.javers.core.model.DummyUserDetails"
        json.rightReference.cdoId == 2
        json.rightReference.entity == "org.javers.core.model.DummyUserDetails"
    }

    def "should be nullSafe when writing leftId & rightId for ReferenceChange" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        ReferenceChange change = referenceChanged(dummyUser().build(),
                "dummyUserDetails",null, null)

        when:
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.rightReference == null
        json.leftReference == null
    }

    def "should be nullSafe when writing left & right value for ValueChange" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        ValueChange change = valueChange(dummyUser("kaz").build(),"bigFlag",null, null)

        when:
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.leftValue == null
        json.rightValue == null

    }

    def "should use custom JsonTypeAdapter when writing Values like LocalDateTime for ValueChange" () {
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        def dob = new LocalDateTime()
        ValueChange change = valueChange(dummyUserWithDate("kaz"),"dob",null, dob)

        when:
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.leftValue ==  null
        json.rightValue == LocalDateTimeTypeAdapter.ISO_FORMATTER.print(dob)
    }

    @Unroll
    def "should write changeType & globalCdoId for #change.class.simpleName"(){
        given:
        JsonConverter jsonConverter = jsonConverter().build()
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        expect:
        def json = new JsonSlurper().parseText(jsonText)
        json.changeType == expectedType
        json.globalCdoId.size() == 2
        json.globalCdoId.cdoId == "kaz"
        json.globalCdoId.entity == "org.javers.core.model.DummyUser"

        where:
        change << [newObject(dummyUser("kaz").build()),
                   objectRemoved(dummyUser("kaz").build()),
                   valueChange(dummyUser("kaz").build(),"flag"),
                   referenceChanged(dummyUser("kaz").build(),"dummyUserDetails",dummyUserDetails(1).build(),null),
                   entryChange(dummyUser("kaz").build(),"valueMap"),
                   entryAdded(dummyUser("kaz").build(),"valueMap"),
                   entryRemoved(dummyUser("kaz").build(),"valueMap")
                  ]
        expectedType << [NewObject.simpleName,
                         ObjectRemoved.simpleName,
                         ValueChange.simpleName,
                         ReferenceChange.simpleName,
                         EntryChanged.simpleName,
                         EntryAdded.simpleName,
                         EntryRemoved.simpleName
                         ]

    }

    def "should write UnboundedValueObjectId & property for unbounded ValueObject property change"() {
        given:
        def jsonConverter = jsonConverter().build()
        def change = unboundedValueObjectPropertyChange(DummyAddress, "street", "Street 1", "Street 2");

        when:
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        then:
        def json = new JsonSlurper().parseText(jsonText)
        json.property == "street"
        json.globalCdoId.size() == 2
        json.globalCdoId.cdoId == "/"
        json.globalCdoId.valueObject == "org.javers.core.model.DummyAddress"
    }

    @Unroll
    def "should write ValueObjectId ownerId of class #ownerId.class.getSimpleName() "() {
        given:
        def jsonSlurper = new JsonSlurper()
        def jsonConverter = jsonConverter().build()
        ValueChange change = valueObjectPropertyChange(ownerId, DummyNetworkAddress, "addres", "fragmentProp", "any", "any2")
        String jsonText = jsonConverter.toJson(change)
        println(jsonText)

        expect:
        def json = jsonSlurper.parseText(jsonText)
        json.property == "addres"
        json.globalCdoId.size() == 3
        json.globalCdoId.fragment == "fragmentProp"
        json.globalCdoId.ownerId == jsonSlurper.parseText(expectedOwnerIdJson)
        json.globalCdoId.valueObject == "org.javers.core.model.DummyNetworkAddress"

        where:
        ownerId  << [instanceId(dummyUserDetails(1).build()), //bounded to entity
                     unboundedValueObjectId(DummyAddress) //bounded to unbounded valueObject
                    ]
        expectedOwnerIdJson << [
                """{
                     "entity": "org.javers.core.model.DummyUserDetails",
                     "cdoId": 1
                }""",
                """{
                    "valueObject": "org.javers.core.model.DummyAddress",
                    "cdoId": "/"
                }"""
        ]

    }
}