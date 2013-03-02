/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.entity.model;

import com.google.common.base.Function;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class Space {

    public static Space create(Reader reader, Function<String, UUID> generateUUIDs) throws IOException {
        Space result;
        try {
            result = new ObjectMapper().readValue(reader, Space.class);
        } catch (JsonMappingException e) {
            if (e.getCause() instanceof IllegalEntityDefinition)
                throw (IllegalEntityDefinition) e.getCause();
            throw e;
        }
        result.visit(new Validator(generateUUIDs));
        return result;
    }

    public static Space readSpace(String fileName, Class<?> forClass, Function<String, UUID> generateUUIDs) {
        try (InputStream is = forClass.getResourceAsStream(fileName)) {
            if (is == null) {
                throw new RuntimeException("resource not found: " + fileName);
            }
            Reader reader = new BufferedReader(new InputStreamReader(is, "utf-8"));
            return create(reader, generateUUIDs);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <E extends Exception> void visit(EntityVisitor<E> visitor) throws E {
        for (Map.Entry<String, Entity> entry : entities.entrySet()) {
            entry.getValue().accept(entry.getKey(), visitor);
        }
    }

    public Map<String, Entity> getEntities() {
        return entities;
    }

    @SuppressWarnings("unused")
    void setEntities(Map<String, Entity> entities) {
        this.entities = Collections.unmodifiableMap(new TreeMap<>(entities));
    }

    @Override
    public String toString() {
        return entities.toString();
    }

    public static Space create(Map<String, Entity> entities, Function<String, UUID> generateUUIDs) {
        Space space = new Space();
        space.setEntities(entities);
        space.visit(new Validator(generateUUIDs));
        return space;
    }

    public void generateJson(JsonGenerator json) throws IOException {
        json.writeStartObject();
        if (!entities.isEmpty()) {
            json.writeObjectFieldStart("entities");
            visit(new JsonEntityFormatter(json));
            json.writeEndObject();
        }
        json.writeEndObject();
    }

    public String toJson() {
        try {
            StringWriter writer = new StringWriter();
            JsonGenerator generator = jsonFactory.createJsonGenerator(writer);
            generateJson(generator);
            generator.flush();
            writer.flush();
            return writer.toString();
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Space() {}

    private Map<String, Entity> entities = Collections.emptyMap();

    private static final JsonFactory jsonFactory = createJsonFactory();

    private static JsonFactory createJsonFactory() {
        JsonFactory factory = new JsonFactory();
        factory.setCodec(new ObjectMapper());
        return factory;
    }

    private static class Validator extends AbstractEntityVisitor {

        @Override
        public void visitEntity(String name, Entity entity) {
            if (needsUUID(entity.uuid()))
                entity.setUuid(uuidGenerator.apply(name));
            ensureUnique(name);
            if (entity.getAttributes() == null || entity.getAttributes().isEmpty())
                throw new IllegalEntityDefinition("no attributes set for entity: " + name);
        }

        @Override
        public void visitScalar(String name, Attribute scalar) {
            if (needsUUID(scalar.getUUID()))
                scalar.setUuid(uuidGenerator.apply(name));
            if (scalar.getType() == null)
                throw new IllegalEntityDefinition("no type set for scalar");
            if (scalar.getAttributes() != null)
                throw new IllegalEntityDefinition("attributes can't be set for scalar");
        }

        @Override
        public void visitCollection(String name, Attribute collection) {
            if (needsUUID(collection.getUUID()))
                collection.setUuid(uuidGenerator.apply(name));
            ensureUnique(name);
            if (collection.getType() != null)
                throw new IllegalEntityDefinition("type can't be set for collection");
            if (collection.getAttributes() == null || collection.getAttributes().isEmpty())
                throw new IllegalEntityDefinition("no attributes set for collection");
        }

        private boolean needsUUID(UUID uuid) {
            if (uuid == null)
                return true;
            if (!uuids.add(uuid))
                throw new IllegalEntityDefinition("duplicate uuid found: " + uuid);
            return false;
        }

        private void ensureUnique(String name) {
            if (!collectionNames.add(name))
                throw new IllegalEntityDefinition("duplicate name within entity and collections: " + name);
        }

        private Validator(Function<String, UUID> uuidGenerator) {
            this.uuidGenerator = uuidGenerator == null ? requireUUIDs : uuidGenerator;
        }

        private final Set<UUID> uuids = new HashSet<>();
        private final Set<String> collectionNames = new HashSet<>();
        private final Function<String, UUID> uuidGenerator;
    }

    public static final Function<String, UUID> randomUUIDs = new Function<String, UUID>() {
        @Override
        public UUID apply(String input) {
            return UUID.randomUUID();
        }
    };

    public static final Function<String, UUID> requireUUIDs = new Function<String, UUID>() {
        @Override
        public UUID apply(String input) {
            throw new IllegalEntityDefinition("no uuid specified");
        }
    };
}
