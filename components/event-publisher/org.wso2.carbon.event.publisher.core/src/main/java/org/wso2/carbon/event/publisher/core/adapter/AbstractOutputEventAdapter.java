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
package org.wso2.carbon.event.publisher.core.adapter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.wso2.carbon.event.publisher.core.Property;
import org.wso2.carbon.event.publisher.core.config.OutputAdaptorConfiguration;

import java.util.List;

/**
 * This is a EventAdaptor type. these interface let users to publish subscribe messages according to
 * some type. this type can either be local, jms or ws
 */
public abstract class AbstractOutputEventAdapter {

    private static final Log log = LogFactory.getLog(AbstractOutputEventAdapter.class);
    private static final String OUTPUT_EVENT_ADAPTOR = "Output Event Adaptor";
    private static final String EVENT_TRACE_LOGGER = "EVENT_TRACE_LOGGER";
    private Logger trace = Logger.getLogger(EVENT_TRACE_LOGGER);
    private OutputEventAdapterDto outputEventAdapterDto;

    protected AbstractOutputEventAdapter() {
        init();
        this.outputEventAdapterDto = new OutputEventAdapterDto();
        this.outputEventAdapterDto.setEventAdaptorTypeName(this.getName());
        this.outputEventAdapterDto.setSupportedMessageTypes(this.getSupportedOutputMessageTypes());
        outputEventAdapterDto.setAdaptorPropertyList(((this)).getOutputAdaptorProperties());

    }

    public OutputEventAdapterDto getOutputEventAdapterDto() {
        return outputEventAdapterDto;
    }

    /**
     * returns the name of the output event adaptor type
     *
     * @return event adaptor type name
     */
    protected abstract String getName();

    /**
     * To get the information regarding supported message types event adaptor
     *
     * @return List of supported output message types
     */
    protected abstract List<String> getSupportedOutputMessageTypes();

    /**
     * any initialization can be done in this method
     */
    protected abstract void init();

    /**
     * the information regarding the adaptor related properties of a specific event adaptor type
     *
     * @return List of properties related to output event adaptor
     */
    protected abstract List<Property> getOutputAdaptorProperties();

    /**
     * publish a message to a given connection.
     *
     * @param message               - message to send
     * @param outputAdaptorConfiguration
     * @param tenantId
     */
    //todo remove
    public void publishCall(Object message, OutputAdaptorConfiguration outputAdaptorConfiguration, int tenantId) {
        publish(message, outputAdaptorConfiguration, tenantId);
    }

    /**
     * publish a message to a given connection.
     *
     * @param message               - message to send
     * @param outputAdaptorConfiguration
     * @param tenantId
     */
    protected abstract void publish(Object message, OutputAdaptorConfiguration outputAdaptorConfiguration, int tenantId);


    /**
     * publish test message to check the connection with the event adaptor configuration.
     *
     * @param outputAdaptorConfiguration - event adaptor configuration to be used
     * @param tenantId
     */
    public abstract void testConnection(OutputAdaptorConfiguration outputAdaptorConfiguration, int tenantId);


    //todo remove
    public abstract void removeConnectionInfo(OutputAdaptorConfiguration outputAdaptorConfiguration, int tenantId);


}