package implementations.util;

import io.protostuff.LinkedBuffer;
import io.protostuff.ProtobufIOUtil;
import io.protostuff.Schema;
import io.protostuff.runtime.RuntimeSchema;

public class SerializeWrapSup {
    private Object key;
    private Object value;

    public SerializeWrapSup(Object key, Object value) {
        this.key = key;
        this.value = value;
    }

    public Object serialization(Object o) {
        Schema scow = RuntimeSchema.getSchema(ObjectWrap.class);
        LinkedBuffer buffer = LinkedBuffer.allocate(1048576);
        buffer.clear();
        ObjectWrap objW = new ObjectWrap(o);
        return ProtobufIOUtil.toByteArray(objW, scow, buffer);
    }

    public void magic() {
        this.value = serialization(this.value);
        this.key = serialization(this.key);
    }

    public Object key() {
        return key;
    }

    public Object value() {
        return value;
    }
}
