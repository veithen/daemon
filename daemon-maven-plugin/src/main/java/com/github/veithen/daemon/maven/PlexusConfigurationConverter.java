/*-
 * #%L
 * Daemon Tools
 * %%
 * Copyright (C) 2012 - 2021 Andreas Veithen
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.configuration.PlexusConfiguration;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;

final class PlexusConfigurationConverter {
    private PlexusConfigurationConverter() {}

    private static Object convertFieldValue(PlexusConfiguration config, FieldDescriptor field) {
        JavaType javaType = field.getJavaType();
        switch (javaType) {
            case STRING:
                return config.getValue();
            case INT:
                return Integer.valueOf(config.getValue());
            case MESSAGE:
                return convert(config, field.getMessageType());
            default:
                throw new UnsupportedOperationException("Unsupported field type " + javaType);
        }
    }

    private static List<Object> convertRepeatedFieldValues(
            PlexusConfiguration config, FieldDescriptor field) {
        List<Object> values = new ArrayList<>(config.getChildCount());
        for (PlexusConfiguration child : config.getChildren()) {
            values.add(convertFieldValue(child, field));
        }
        return values;
    }

    public static Message convert(PlexusConfiguration config, Descriptor descriptor) {
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
            if (field.isRepeated()) {
                message.setField(field, convertRepeatedFieldValues(child, field));
            } else {
                message.setField(field, convertFieldValue(child, field));
            }
        }
        return message.build();
    }
}
