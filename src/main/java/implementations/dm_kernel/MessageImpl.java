package implementations.dm_kernel;

import commom.Constants;
import interfaces.kernel.JCL_message;
import io.protostuff.Tag;

public class MessageImpl implements JCL_message{

	
	private static final long serialVersionUID = 5450456847644209521L;

	@Tag(1)
	private int type;
    @Tag(2)
    private byte typeD;

	@Override
	public int getType() {
		// TODO Auto-generated method stub
		return this.type;
	}

	@Override
	public void setType(int type) {
		// TODO Auto-generated method stub
		this.type = type;
	}

	@Override
	public int getMsgType() {
		// TODO Auto-generated method stub
		return Constants.Serialization.MSG;
	}
	
	@Override
	public byte getTypeDevice() {
		// TODO Auto-generated method stub
		return typeD;
	}

	@Override
	public void setTypeDevice(byte typeDevice) {
		typeD = typeDevice;		
	}
}
