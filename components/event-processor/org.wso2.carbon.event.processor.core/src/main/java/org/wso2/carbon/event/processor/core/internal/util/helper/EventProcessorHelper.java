/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.event.processor.core.internal.util.helper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.databridge.commons.Attribute;
import org.wso2.carbon.databridge.commons.AttributeType;
import org.wso2.carbon.databridge.commons.StreamDefinition;
import org.wso2.carbon.event.processor.core.exception.ExecutionPlanConfigurationException;
import org.wso2.carbon.event.processor.core.exception.ExecutionPlanDependencyValidationException;
import org.wso2.carbon.event.processor.core.internal.ds.EventProcessorValueHolder;
import org.wso2.carbon.event.processor.core.internal.util.EventProcessorConstants;
import org.wso2.carbon.event.stream.core.EventStreamService;
import org.wso2.carbon.event.stream.core.exception.EventStreamConfigurationException;
import org.wso2.carbon.ndatasource.common.DataSourceException;
import org.wso2.carbon.ndatasource.core.CarbonDataSource;
import org.wso2.carbon.ndatasource.core.DataSourceManager;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.query.api.ExecutionPlan;
import org.wso2.siddhi.query.api.annotation.Element;
import org.wso2.siddhi.query.api.exception.AttributeNotExistException;
import org.wso2.siddhi.query.api.util.AnnotationHelper;
import org.wso2.siddhi.query.compiler.SiddhiCompiler;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class EventProcessorHelper {

    private static final Log log = LogFactory.getLog(EventProcessorHelper.class);

    /**
     * Returns the execution plan name
     *
     * @param executionPlanAsString executionPlan (taken from code mirror) as a string
     * @return execution plan name as given in @Plan:name('MyPlanName'). Returns null in the absence of @Plan:name('MyPlanName')
     */
    public static String getExecutionPlanName(String executionPlanAsString) {
        String executionPlanName = null;
        ExecutionPlan executionPlan = SiddhiCompiler.parse(executionPlanAsString);
        executionPlanName = AnnotationHelper.getAnnotationElement(EventProcessorConstants.ANNOTATION_NAME_NAME, null, executionPlan.getAnnotations()).getValue();
        return executionPlanName;
    }

    public static void validateExecutionPlan(String executionPlan)
            throws ExecutionPlanConfigurationException, ExecutionPlanDependencyValidationException {

        String planName;
        int i = 0;      //this is maintained for giving more context info in error messages, when throwing exceptions.
        ArrayList<String> importedStreams = new ArrayList<String>();
        ArrayList<String> exportedStreams = new ArrayList<String>();
        Pattern databridgeStreamNamePattern = Pattern.compile(EventProcessorConstants.DATABRIDGE_STREAM_REGEX);
        Pattern streamVersionPattern = Pattern.compile(EventProcessorConstants.STREAM_VER_REGEX);

        ExecutionPlan parsedExecPlan = SiddhiCompiler.parse(executionPlan);
        Element element = AnnotationHelper.getAnnotationElement(EventProcessorConstants.ANNOTATION_NAME_NAME, null, parsedExecPlan.getAnnotations());
        if (element == null) {                                                                        // check if plan name is given
            throw new ExecutionPlanConfigurationException("Execution plan name is not given. Please specify execution plan name using the annotation " +
                    "'" +
                    EventProcessorConstants.ANNOTATION_TOKEN_AT +
                    EventProcessorConstants.ANNOTATION_PLAN +
                    EventProcessorConstants.ANNOTATION_TOKEN_COLON +
                    EventProcessorConstants.ANNOTATION_NAME_NAME +
                    EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +
                    EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                    "executionPlanNameHere" +
                    EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                    EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET +
                    "'");
        }
        planName = element.getValue();
        if (planName.equals("")) {
            throw new ExecutionPlanConfigurationException("Execution plan name is empty. Hence the plan is invalid");
        }
        if (planName.trim().contains(" ")) {
            throw new ExecutionPlanConfigurationException("Execution plan name '" + planName + "' contains whitespaces. Please remove whitespaces.");
        }

        Map<String, org.wso2.siddhi.query.api.definition.StreamDefinition> streamDefMap = parsedExecPlan.getStreamDefinitionMap();
        for (Map.Entry<String, org.wso2.siddhi.query.api.definition.StreamDefinition> entry : streamDefMap.entrySet()) {
            Element importElement = AnnotationHelper.getAnnotationElement(EventProcessorConstants.ANNOTATION_IMPORT, null, entry.getValue().getAnnotations());
            Element exportElement = AnnotationHelper.getAnnotationElement(EventProcessorConstants.ANNOTATION_EXPORT, null, entry.getValue().getAnnotations());
            if (importElement != null && exportElement != null) {
                throw new ExecutionPlanConfigurationException("Same stream definition has being imported and exported. Please correct " + i +
                        "th of the " + parsedExecPlan.getStreamDefinitionMap().size() + "stream definition, with stream id '" + entry.getKey() + "'");
            }
            if (importElement != null) {  //Treating import & export cases separately to give more specific error messages.
                String atImportLiteral = EventProcessorConstants.ANNOTATION_TOKEN_AT + EventProcessorConstants.ANNOTATION_IMPORT;
                String importElementValue = importElement.getValue();
                if (importElementValue == null || importElementValue.trim().isEmpty()) {
                    throw new ExecutionPlanConfigurationException("Imported stream cannot be empty as in '" +
                            atImportLiteral +
                            EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +
                            EventProcessorConstants.SIDDHI_SINGLE_QUOTE + EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                            EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET +
                            "'. Please correct " + i + "th of the " + parsedExecPlan.getStreamDefinitionMap().size() +
                            "stream definition, with stream id '" + entry.getKey() + "'");
                }
                String[] streamIdComponents = importElementValue.split(EventProcessorConstants.STREAM_SEPARATOR);
                if (streamIdComponents.length != 2) {
                    throw new ExecutionPlanConfigurationException("Found malformed " + atImportLiteral +
                            " element '" + importElementValue + "'. " + atImportLiteral +
                            " annotation should take the form '" + atImportLiteral +
                            EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +
                            EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                            "streamName" + EventProcessorConstants.STREAM_SEPARATOR + "StreamVersion" +
                            EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                            EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET +
                            "'. There should be a '" + EventProcessorConstants.STREAM_SEPARATOR + "' character, separating the streamName and its version");
                }
                if ((!databridgeStreamNamePattern.matcher(streamIdComponents[0].trim()).matches())) {
                    throw new ExecutionPlanConfigurationException("Invalid imported stream name[" + streamIdComponents[0] + "] in execution plan:" + planName +
                            ". Stream name should match the regex '" + EventProcessorConstants.DATABRIDGE_STREAM_REGEX + "'");
                }
                Matcher m = streamVersionPattern.matcher(streamIdComponents[1].trim());
                if (!m.matches()) {
                    throw new ExecutionPlanConfigurationException("Invalid stream version [" + streamIdComponents[1] + "] for stream name " + streamIdComponents[0] + " in execution plan: " + planName +
                            ". Stream version should match the regex '" + EventProcessorConstants.STREAM_VER_REGEX + "'");
                }
                validateSiddhiStreamWithDatabridgeStream(streamIdComponents[0], streamIdComponents[1], entry.getValue());
                if (exportedStreams.contains(importElementValue)) {                                   // check if same stream has been imported and exported.
                    throw new ExecutionPlanConfigurationException("Imported stream '" + importElementValue + "' is also among the exported streams. Hence the execution plan is invalid");
                }
                importedStreams.add(importElementValue);
            }
            if (exportElement != null) {
                String atExportLiteral = EventProcessorConstants.ANNOTATION_TOKEN_AT + EventProcessorConstants.ANNOTATION_EXPORT;
                String exportElementValue = exportElement.getValue();
                if (exportElementValue == null || exportElementValue.trim().isEmpty()) {
                    throw new ExecutionPlanConfigurationException("Exported stream cannot be empty as in '" +
                            atExportLiteral +
                            EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +
                            EventProcessorConstants.SIDDHI_SINGLE_QUOTE + EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                            EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET +
                            "'. Please correct " + i + "th of the " + parsedExecPlan.getStreamDefinitionMap().size() +
                            "stream definition, with stream id '" + entry.getKey());
                }
                String[] streamIdComponents = exportElementValue.split(EventProcessorConstants.STREAM_SEPARATOR);
                if (streamIdComponents.length != 2) {
                    throw new ExecutionPlanConfigurationException("Found malformed " + atExportLiteral + " element '" + exportElementValue + "'. " + atExportLiteral +
                            " annotation should take the form '" +
                            atExportLiteral +
                            EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +
                            EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                            "streamName" + EventProcessorConstants.STREAM_SEPARATOR + "StreamVersion" +
                            EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                            EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET +
                            "'. There should be a '" + EventProcessorConstants.STREAM_SEPARATOR + "' character, separating the streamName and its version");
                }
                if ((!databridgeStreamNamePattern.matcher(streamIdComponents[0].trim()).matches())) {
                    throw new ExecutionPlanConfigurationException("Invalid exported stream name[" + streamIdComponents[0] + "] in execution plan:" + planName +
                            ". Stream name should match the regex '" + EventProcessorConstants.DATABRIDGE_STREAM_REGEX + "'");
                }
                Matcher m = streamVersionPattern.matcher(streamIdComponents[1].trim());
                if (!m.matches()) {
                    throw new ExecutionPlanConfigurationException("Invalid stream version [" + streamIdComponents[1] + "] for stream name " + streamIdComponents[0] + " in execution plan: " + planName +
                            ". Stream version should match the regex '" + EventProcessorConstants.STREAM_VER_REGEX + "'");
                }
                validateSiddhiStreamWithDatabridgeStream(streamIdComponents[0], streamIdComponents[1], entry.getValue());
                if (importedStreams.contains(exportElementValue)) {
                    throw new ExecutionPlanConfigurationException("Exported stream '" + exportElementValue + "' is also among the imported streams. Hence the execution plan is invalid");
                }
                exportedStreams.add(exportElementValue);
            }
            i++;
        }

        SiddhiManager siddhiManager = EventProcessorValueHolder.getSiddhiManager();
        loadDataSourceConfiguration(siddhiManager);
        try {
            siddhiManager.validateExecutionPlan(executionPlan);
        } catch (Throwable t) {
            throw new ExecutionPlanConfigurationException(t.getMessage(), t);
        }
    }

    private static boolean validateSiddhiStreamWithDatabridgeStream(String streamName, String streamVersion,
                                                                    org.wso2.siddhi.query.api.definition.StreamDefinition siddhiStreamDefinition)
            throws ExecutionPlanConfigurationException, ExecutionPlanDependencyValidationException {
        if (siddhiStreamDefinition == null) {
            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                    + streamVersion, "Cannot validate null Siddhi stream for the stream: " + streamName
                    + EventProcessorConstants.STREAM_SEPARATOR + streamVersion + " ");
        }
        EventStreamService eventStreamService = EventProcessorValueHolder.getEventStreamService();
        try {
            StreamDefinition streamDefinition = eventStreamService.getStreamDefinition(streamName, streamVersion);
            if (streamDefinition != null) {
                String siddhiAttributeName;
                int attributeCount = 0;
                int streamSize = (streamDefinition.getMetaData() == null ? 0 : streamDefinition.getMetaData().size())
                        + (streamDefinition.getCorrelationData() == null ? 0 : streamDefinition.getCorrelationData().size())
                        + (streamDefinition.getPayloadData() == null ? 0 : streamDefinition.getPayloadData().size());
                if (siddhiStreamDefinition.getAttributeList().size() != streamSize) {
                    throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                            + streamVersion, "No of attributes in stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                            + " do not match the no of attributes in Siddhi stream");
                }
                if (streamDefinition.getMetaData() != null) {
                    for (Attribute attribute : streamDefinition.getMetaData()) {
                        siddhiAttributeName = EventProcessorConstants.META_PREFIX + attribute.getName();
                        org.wso2.siddhi.query.api.definition.Attribute.Type type = siddhiStreamDefinition.getAttributeType(
                                siddhiAttributeName);
                        // null check for type not required since an exception is thrown by Siddhi
                        // StreamDefinition.getAttributeType() method for non-existent attributes
                        if (siddhiStreamDefinition.getAttributePosition(siddhiAttributeName) != attributeCount++) {
                            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                                    + streamVersion, "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                                    + "; Attribute positions do not match for attribute: " + attribute.getName());
                        }
                        if (!isMatchingType(type, attribute.getType())) {
                            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                                    + streamVersion, "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                                    + "; Type mismatch for attribute: " + attribute.getName());
                        }
                    }
                }
                if (streamDefinition.getCorrelationData() != null) {
                    for (Attribute attribute : streamDefinition.getCorrelationData()) {
                        siddhiAttributeName = EventProcessorConstants.CORRELATION_PREFIX + attribute.getName();
                        org.wso2.siddhi.query.api.definition.Attribute.Type type = siddhiStreamDefinition.getAttributeType(
                                siddhiAttributeName);
                        // null check for type not required since an exception is thrown by Siddhi
                        // StreamDefinition.getAttributeType() method for non-existent attributes
                        if (siddhiStreamDefinition.getAttributePosition(siddhiAttributeName) != attributeCount++) {
                            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                                    + streamVersion, "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                                    + "; Attribute positions do not match for attribute: " + attribute.getName());
                        }
                        if (!isMatchingType(type, attribute.getType())) {
                            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                                    + streamVersion, "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                                    + "; Type mismatch for attribute: " + attribute.getName());
                        }
                    }
                }
                if (streamDefinition.getPayloadData() != null) {
                    for (Attribute attribute : streamDefinition.getPayloadData()) {
                        siddhiAttributeName = attribute.getName();
                        org.wso2.siddhi.query.api.definition.Attribute.Type type = siddhiStreamDefinition.getAttributeType(
                                siddhiAttributeName);
                        // null check for type not required since an exception is thrown by Siddhi
                        // StreamDefinition.getAttributeType() method for non-existent attributes
                        if (siddhiStreamDefinition.getAttributePosition(siddhiAttributeName) != attributeCount++) {
                            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                                    + streamVersion, "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                                    + "; Attribute positions do not match for attribute: " + attribute.getName());
                        }
                        if (!isMatchingType(type, attribute.getType())) {
                            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR
                                    + streamVersion, "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion
                                    + "; Type mismatch for attribute: " + attribute.getName());
                        }
                    }
                }
                return true;
            }
        } catch (EventStreamConfigurationException e) {
            throw new ExecutionPlanConfigurationException("Error while validating stream definition with store : " + e.getMessage(), e);
        } catch (AttributeNotExistException e) {
            throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion,
                    e.getMessage());
        }
        throw new ExecutionPlanDependencyValidationException(streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion,
                "Stream " + streamName + EventProcessorConstants.STREAM_SEPARATOR + streamVersion + " does not exist");
    }

    private static boolean isMatchingType(org.wso2.siddhi.query.api.definition.Attribute.Type siddhiType, AttributeType databridgeType) {
        switch (siddhiType) {
            case BOOL:
                return databridgeType == AttributeType.BOOL;
            case STRING:
                return databridgeType == AttributeType.STRING;
            case DOUBLE:
                return databridgeType == AttributeType.DOUBLE;
            case FLOAT:
                return databridgeType == AttributeType.FLOAT;
            case INT:
                return databridgeType == AttributeType.INT;
            case LONG:
                return databridgeType == AttributeType.LONG;
            default:
                return false;
        }
    }

    /**
     * Sets an annotation name for a given execution plan to be true or false.
     * For example, when an execution plan has the statement "@Plan:statistics('false')" and false need to be set to true,
     * then this helper method can be used.
     *
     * @param executionPlan        Existing execution plan, either having the annotation name set to be true/false,
     *                             or the annotation name is not present in the execution plan at all.
     * @param annotationName       The annotation name which needs to be set to true/false.
     *                             For example, in Siddhi statement @Plan:name('false'), 'name' will be the annotation name.
     * @param isAnnotationNameTrue Whether the annotation name need to be set to true or false.
     * @return New execution plan with the given plan annotation name set to be true.
     */
    public static String setExecutionPlanAnnotationName(String executionPlan, String annotationName, boolean isAnnotationNameTrue) {
        String newExecutionPlan = null;
        String planHeader = "";
        String planBody = "";
        String planHeaderLineRegex = EventProcessorConstants.PLAN_HEADER_LINE_REGEX;

        String regexToBeReplaced = "^\\s*" +        //beginning of line with zero or more whitespaces
                EventProcessorConstants.ANNOTATION_TOKEN_AT +
                EventProcessorConstants.ANNOTATION_PLAN +
                EventProcessorConstants.ANNOTATION_TOKEN_COLON +
                annotationName +
                "\\" + EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +    //bracket is escaped, because the literal is meant.
                EventProcessorConstants.SIDDHI_SINGLE_QUOTE + !isAnnotationNameTrue + EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                "\\" + EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET;     //bracket is escaped, because the literal is meant.

        String replacement = EventProcessorConstants.ANNOTATION_TOKEN_AT +
                EventProcessorConstants.ANNOTATION_PLAN +
                EventProcessorConstants.ANNOTATION_TOKEN_COLON +
                annotationName +
                EventProcessorConstants.ANNOTATION_TOKEN_OPENING_BRACKET +
                EventProcessorConstants.SIDDHI_SINGLE_QUOTE + isAnnotationNameTrue + EventProcessorConstants.SIDDHI_SINGLE_QUOTE +
                EventProcessorConstants.ANNOTATION_TOKEN_CLOSING_BRACKET;

        Matcher matcher = Pattern.compile(regexToBeReplaced, Pattern.MULTILINE).matcher(executionPlan);

        if (matcher.find()) {   //statement with annotation name set to false, is already in the plan; In that case, false will be replaced with true.

            //finding the whitespaces given by the user before "@Plan:name()" statement and prepending those at replacement.
            String[] matchSplitArray = matcher.group().split(EventProcessorConstants.ANNOTATION_TOKEN_AT);
            String whitespaces = "";
            if (matchSplitArray.length > 1) {
                whitespaces += matchSplitArray[0];
            }

            replacement = whitespaces + replacement;
            newExecutionPlan = matcher.replaceFirst(replacement);

        } else {       //statement with annotation name is not there in the plan; it'll be inserted.
            String[] planHeaderArray = executionPlan.split(EventProcessorConstants.SIDDHI_LINE_SEPARATER);
            for (int i = 0; i < planHeaderArray.length; i++) {
                if (planHeaderArray[i].matches(planHeaderLineRegex)) {
                    if (planHeaderArray[i].matches(EventProcessorConstants.END_OF_PLAN_HEADER_COMMENT_REGEX)) {
                        break;
                    }
                    planHeader += planHeaderArray[i] + EventProcessorConstants.SIDDHI_LINE_SEPARATER;
                } else {
                    break;
                }
            }
            planBody = executionPlan.replace(planHeader, "");
            newExecutionPlan = planHeader + replacement + EventProcessorConstants.SIDDHI_LINE_SEPARATER +
                    EventProcessorConstants.SIDDHI_LINE_SEPARATER + planBody;
        }
        return newExecutionPlan;
    }

    public static void loadDataSourceConfiguration(SiddhiManager siddhiManager) {
        try {
            int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
            if (tenantId > -1) {
                DataSourceManager.getInstance().initTenant(tenantId);
            }
            List<CarbonDataSource> dataSources = EventProcessorValueHolder.getDataSourceService().getAllDataSources();
            for (CarbonDataSource cds : dataSources) {
                try {
                    if (cds.getDSObject() instanceof DataSource) {
                        siddhiManager.setDataSource(cds.getDSMInfo().getName(), (DataSource) cds.getDSObject());
                    }
                } catch (Exception e) {
                    log.error("Unable to add the datasource" + cds.getDSMInfo().getName(), e);
                }
            }
        } catch (DataSourceException e) {
            log.error("Unable to populate the data sources in Siddhi engine.", e);
        }
    }
}
