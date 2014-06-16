package otp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TimeZone;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;

public class AnalystRequest extends RoutingRequest{
	
	private static PrototypeAnalystRequest prototypeRequest = new PrototypeAnalystRequest();
	
	public String sptId;
	public String graphId;
	
	static public AnalystRequest create(String graphId, GenericLocation latLon) throws IOException, NoSuchAlgorithmException {
		
		AnalystRequest request = (AnalystRequest)prototypeRequest.clone();
		
		request.graphId = graphId;
	
        if (request.arriveBy)
            request.setTo(latLon);
        else
            request.setFrom(latLon);
        
		return request;
	}
	
	public void calcHash() throws IOException, NoSuchAlgorithmException {
	
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(baos);
	    oos.writeObject(this.toString() + this.modes.toString());

	    oos.close();

	    MessageDigest md5 = MessageDigest.getInstance("MD5");
	    md5.update(baos.toByteArray());

	    BigInteger hash = new BigInteger(1, md5.digest());
	    sptId = graphId + "_" + hash.toString(16);

	}
	
	
}