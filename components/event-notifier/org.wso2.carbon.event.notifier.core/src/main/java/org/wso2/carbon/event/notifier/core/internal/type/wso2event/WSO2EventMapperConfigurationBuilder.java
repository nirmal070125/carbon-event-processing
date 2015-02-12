/*
*  Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.wso2.carbon.event.notifier.core.internal.type.wso2event;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.event.notifier.core.config.EventNotifierConstants;
import org.wso2.carbon.event.notifier.core.config.OutputMapping;
import org.wso2.carbon.event.notifier.core.config.mapping.WSO2EventOutputMapping;
import org.wso2.carbon.event.notifier.core.exception.EventNotifierConfigurationException;
import org.wso2.carbon.event.notifier.core.exception.EventNotifierValidationException;
import org.wso2.carbon.event.notifier.core.internal.config.EventOutputProperty;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * This class is used to read the values of the event builder configuration defined in XML configuration files
 */
public class WSO2EventMapperConfigurationBuilder {

    private WSO2EventMapperConfigurationBuilder() {

    }

    public static OutputMapping fromOM(
            OMElement mappingElement)
            throws EventNotifierValidationException, EventNotifierConfigurationException {

        WSO2EventOutputMapping wso2EventOutputMapping = new WSO2EventOutputMapping();

        String customMappingEnabled = mappingElement.getAttributeValue(new QName(EventNotifierConstants.EF_ATTR_CUSTOM_MAPPING));
        if (customMappingEnabled == null || (customMappingEnabled.equals(EventNotifierConstants.TM_VALUE_ENABLE))) {
            wso2EventOutputMapping.setCustomMappingEnabled(true);
            if (!validateWSO2EventMapping(mappingElement)) {
                throw new EventNotifierConfigurationException("WS02Event Mapping is not valid, check the output mapping");
            }
            wso2EventOutputMapping.setCustomMappingEnabled(true);

            OMElement metaMappingElement = mappingElement.getFirstChildWithName(
                    new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_TO_METADATA_PROPERTY));

            if (metaMappingElement != null) {
                Iterator metaPropertyIterator = metaMappingElement.getChildrenWithName(new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_PROPERTY));
                while (metaPropertyIterator.hasNext()) {
                    OMElement propertyOMElement = (OMElement) metaPropertyIterator.next();

                    EventOutputProperty eventOutputProperty = getWSO2EventOutputPropertyFromOM(propertyOMElement);
                    wso2EventOutputMapping.addMetaWSO2EventOutputPropertyConfiguration(eventOutputProperty);

                }
            }


            OMElement correlationMappingElement = mappingElement.getFirstChildWithName(
                    new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_TO_CORRELATION_PROPERTY));

            if (correlationMappingElement != null) {
                Iterator correlationPropertyIterator = correlationMappingElement.getChildrenWithName(new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_PROPERTY));
                while (correlationPropertyIterator.hasNext()) {
                    OMElement propertyOMElement = (OMElement) correlationPropertyIterator.next();

                    EventOutputProperty eventOutputProperty = getWSO2EventOutputPropertyFromOM(propertyOMElement);
                    wso2EventOutputMapping.addCorrelationWSO2EventOutputPropertyConfiguration(eventOutputProperty);

                }
            }

            OMElement payloadMappingElement = mappingElement.getFirstChildWithName(
                    new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_TO_PAYLOAD_PROPERTY));
            if (payloadMappingElement != null) {
                Iterator payloadPropertyIterator = payloadMappingElement.getChildrenWithName(new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_PROPERTY));
                while (payloadPropertyIterator.hasNext()) {
                    OMElement propertyOMElement = (OMElement) payloadPropertyIterator.next();

                    EventOutputProperty eventOutputProperty = getWSO2EventOutputPropertyFromOM(propertyOMElement);
                    wso2EventOutputMapping.addPayloadWSO2EventOutputPropertyConfiguration(eventOutputProperty);
                }
            }
        } else {
            wso2EventOutputMapping.setCustomMappingEnabled(false);
        }

        return wso2EventOutputMapping;
    }

    private static EventOutputProperty getWSO2EventOutputPropertyFromOM(OMElement omElement) {

        OMElement propertyFromElement = omElement.getFirstChildWithName(new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_FROM_PROPERTY));
        OMElement propertyToElement = omElement.getFirstChildWithName(new QName(EventNotifierConstants.EF_CONF_NS, EventNotifierConstants.EF_ELE_ENDPOINT_PROPERTY));

        String name = propertyToElement.getAttributeValue(new QName(EventNotifierConstants.EF_ATTR_NAME));
        String valueOf = propertyFromElement.getAttributeValue(new QName(EventNotifierConstants.EF_ATTR_NAME));
        AttributeType type = EventNotifierConstants.STRING_ATTRIBUTE_TYPE_MAP.get(propertyToElement.getAttributeValue(new QName(EventNotifierConstants.EF_ATTR_TYPE)));

        return new EventOutputProperty(name, valueOf, type);

    }


    private static boolean validateWSO2EventMapping(OMElement omElement) {

        List<String> supportedChildTags = new ArrayList<String>();
        supportedChildTags.add(EventNotifierConstants.EF_ELE_TO_METADATA_PROPERTY);
        supportedChildTags.add(EventNotifierConstants.EF_ELE_TO_CORRELATION_PROPERTY);
        supportedChildTags.add(EventNotifierConstants.EF_ELE_TO_PAYLOAD_PROPERTY);

        int count = 0;
        Iterator<OMElement> mappingIterator = omElement.getChildElements();
        while (mappingIterator.hasNext()) {
            OMElement childElement = mappingIterator.next();
            String childTag = childElement.getLocalName();
            if (!supportedChildTags.contains(childTag)) {
                return false;
            }
            count++;
        }

        return count != 0;

    }


    public static OMElement outputMappingToOM(
            OutputMapping outputMapping, OMFactory factory) {

        WSO2EventOutputMapping wso2EventOutputMapping = (WSO2EventOutputMapping) outputMapping;

        List<EventOutputProperty> metaWSO2EventPropertyConfiguration = wso2EventOutputMapping.getMetaWSO2EventOutputPropertyConfiguration();
        List<EventOutputProperty> correlationWSO2EventPropertyConfiguration = wso2EventOutputMapping.getCorrelationWSO2EventOutputPropertyConfiguration();
        List<EventOutputProperty> payloadWSO2EventPropertyConfiguration = wso2EventOutputMapping.getPayloadWSO2EventOutputPropertyConfiguration();

        OMElement mappingOMElement = factory.createOMElement(new QName(
                EventNotifierConstants.EF_ELE_MAPPING_PROPERTY));
        mappingOMElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);

        mappingOMElement.addAttribute(EventNotifierConstants.EF_ATTR_TYPE, EventNotifierConstants.EF_WSO2EVENT_MAPPING_TYPE, null);

        if (wso2EventOutputMapping.isCustomMappingEnabled()) {
            mappingOMElement.addAttribute(EventNotifierConstants.EF_ATTR_CUSTOM_MAPPING, EventNotifierConstants.TM_VALUE_ENABLE, null);


            if (metaWSO2EventPropertyConfiguration.size() > 0) {
                OMElement metaOMElement = factory.createOMElement(new QName(EventNotifierConstants.EF_ELE_TO_METADATA_PROPERTY));
                metaOMElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);

                for (EventOutputProperty eventOutputProperty : metaWSO2EventPropertyConfiguration) {
                    metaOMElement.addChild(getPropertyOmElement(factory, eventOutputProperty));
                }
                mappingOMElement.addChild(metaOMElement);
            }

            if (correlationWSO2EventPropertyConfiguration.size() > 0) {
                OMElement correlationOMElement = factory.createOMElement(new QName(EventNotifierConstants.EF_ELE_TO_CORRELATION_PROPERTY));
                correlationOMElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);

                for (EventOutputProperty eventOutputProperty : correlationWSO2EventPropertyConfiguration) {
                    correlationOMElement.addChild(getPropertyOmElement(factory, eventOutputProperty));
                }
                mappingOMElement.addChild(correlationOMElement);
            }

            if (payloadWSO2EventPropertyConfiguration.size() > 0) {
                OMElement payloadOMElement = factory.createOMElement(new QName(EventNotifierConstants.EF_ELE_TO_PAYLOAD_PROPERTY));
                payloadOMElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);

                for (EventOutputProperty eventOutputProperty : payloadWSO2EventPropertyConfiguration) {
                    payloadOMElement.addChild(getPropertyOmElement(factory, eventOutputProperty));
                }
                mappingOMElement.addChild(payloadOMElement);
            }
        } else {
            mappingOMElement.addAttribute(EventNotifierConstants.EF_ATTR_CUSTOM_MAPPING, EventNotifierConstants.TM_VALUE_DISABLE, null);
        }

        return mappingOMElement;
    }

    private static OMElement getPropertyOmElement(OMFactory factory,
                                                  EventOutputProperty eventOutputProperty) {

        OMElement propertyOMElement = factory.createOMElement(new QName(EventNotifierConstants.EF_ELE_PROPERTY));
        propertyOMElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);

        OMElement fromElement = factory.createOMElement(new QName(EventNotifierConstants.EF_ELE_FROM_PROPERTY));
        fromElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);
        fromElement.addAttribute(EventNotifierConstants.EF_ATTR_NAME, eventOutputProperty.getValueOf(), null);

        OMElement toElement = factory.createOMElement(new QName(EventNotifierConstants.EF_ELE_ENDPOINT_PROPERTY));
        toElement.declareDefaultNamespace(EventNotifierConstants.EF_CONF_NS);
        toElement.addAttribute(EventNotifierConstants.EF_ATTR_NAME, eventOutputProperty.getName(), null);
        toElement.addAttribute(EventNotifierConstants.EF_ATTR_TYPE, getAttributeType(eventOutputProperty.getType()), null);

        propertyOMElement.addChild(fromElement);
        propertyOMElement.addChild(toElement);

        return propertyOMElement;

    }

    private static String getAttributeType(AttributeType attributeType) {
        Map<String, AttributeType> attributeMap = EventNotifierConstants.STRING_ATTRIBUTE_TYPE_MAP;
        for (Map.Entry<String, AttributeType> entry : attributeMap.entrySet()) {
            if (entry.getValue().equals(attributeType)) {
                return entry.getKey();
            }
        }
        return null;
    }

}




