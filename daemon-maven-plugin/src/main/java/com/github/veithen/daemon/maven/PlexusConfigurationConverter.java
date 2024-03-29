/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2024 Andreas Veithen
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.github.veithen.daemon.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import com.github.veithen.daemon.ProtoOptions;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;

final class PlexusConfigurationConverter {
    private PlexusConfigurationConverter() {}

    private static Object convertFieldValue(
            PlexusConfiguration config, ExpressionEvaluator evaluator, FieldDescriptor field)
            throws ExpressionEvaluationException {
        JavaType javaType = field.getJavaType();
        if (javaType == JavaType.MESSAGE) {
            return convert(config, evaluator, field.getMessageType());
        }
        String value = evaluator.evaluate(config.getValue()).toString();
        switch (javaType) {
            case STRING:
                if (field.getOptions().getExtension(ProtoOptions.isFile)) {
                    return evaluator.alignToBaseDirectory(new File(value)).getAbsolutePath();
                }
                return value;
            case INT:
                return Integer.valueOf(value);
            case BOOLEAN:
                return Boolean.valueOf(value);
            default:
                throw new UnsupportedOperationException("Unsupported field type " + javaType);
        }
    }

    private static List<Object> convertRepeatedFieldValues(
            PlexusConfiguration config, ExpressionEvaluator evaluator, FieldDescriptor field)
            throws ExpressionEvaluationException {
        List<Object> values = new ArrayList<>(config.getChildCount());
        for (PlexusConfiguration child : config.getChildren()) {
            values.add(convertFieldValue(child, evaluator, field));
        }
        return values;
    }

    private static List<Message> convertMapFieldValues(
            PlexusConfiguration config, ExpressionEvaluator evaluator, FieldDescriptor field)
            throws ExpressionEvaluationException {
        List<Message> values = new ArrayList<>(config.getChildCount());
        for (PlexusConfiguration child : config.getChildren()) {
            Descriptor descriptor = field.getMessageType();
            Builder message = DynamicMessage.newBuilder(descriptor);
            message.setField(descriptor.findFieldByNumber(1), child.getName());
            FieldDescriptor valueField = descriptor.findFieldByNumber(2);
            message.setField(valueField, convertFieldValue(child, evaluator, valueField));
            values.add(message.build());
        }
        return values;
    }

    public static Message convert(
            PlexusConfiguration config, ExpressionEvaluator evaluator, Descriptor descriptor)
            throws ExpressionEvaluationException {
        Builder message = DynamicMessage.newBuilder(descriptor);
        Map<String, FieldDescriptor> fieldMap = new HashMap<>();
        descriptor
                .getFields()
                .forEach(
                        f -> {
                            fieldMap.put(f.getJsonName(), f);
                        });
        for (PlexusConfiguration child : config.getChildren()) {
            FieldDescriptor field = fieldMap.get(child.getName());
            if (field == null) {
                throw new IllegalArgumentException("Unexpected field " + child.getName());
            }
            if (field.isMapField()) {
                message.setField(field, convertMapFieldValues(child, evaluator, field));
            } else if (field.isRepeated()) {
                message.setField(field, convertRepeatedFieldValues(child, evaluator, field));
            } else {
                message.setField(field, convertFieldValue(child, evaluator, field));
            }
        }
        return message.build();
    }
}
