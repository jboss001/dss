package eu.europa.esig.dss.xades.signature;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.MimeType;
import eu.europa.esig.dss.test.signature.AbstractCounterSignatureTest;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.XAdESTimestampParameters;
import eu.europa.esig.dss.xades.definition.xades132.XAdES132Element;
import eu.europa.esig.validationreport.jaxb.SAMessageDigestType;

public abstract class AbstractXAdESCounterSignatureTest extends AbstractCounterSignatureTest<XAdESSignatureParameters, 
				XAdESTimestampParameters, XAdESCounterSignatureParameters> {
	
	@Override
	protected void onDocumentSigned(byte[] byteArray) {
		super.onDocumentSigned(byteArray);
		
		assertTrue(new String(byteArray).contains(XAdES132Element.COUNTER_SIGNATURE.getTagName()));
	}

	@Override
	protected List<DSSDocument> getOriginalDocuments() {
		return Collections.singletonList(getDocumentToSign());
	}

	@Override
	protected MimeType getExpectedMime() {
		return MimeType.XML;
	}

	@Override
	protected boolean isBaselineT() {
		SignatureLevel signatureLevel = getSignatureParameters().getSignatureLevel();
		return SignatureLevel.XAdES_BASELINE_LTA.equals(signatureLevel) || SignatureLevel.XAdES_BASELINE_LT.equals(signatureLevel)
				|| SignatureLevel.XAdES_BASELINE_T.equals(signatureLevel) || SignatureLevel.XAdES_A.equals(signatureLevel)
				|| SignatureLevel.XAdES_C.equals(signatureLevel) || SignatureLevel.XAdES_X.equals(signatureLevel)
				|| SignatureLevel.XAdES_XL.equals(signatureLevel);
	}

	@Override
	protected boolean isBaselineLTA() {
		SignatureLevel signatureLevel = getSignatureParameters().getSignatureLevel();
		return SignatureLevel.XAdES_BASELINE_LTA.equals(signatureLevel) || SignatureLevel.XAdES_A.equals(signatureLevel);
	}

	@Override
	protected void validateETSIMessageDigest(SAMessageDigestType md) {
		assertNull(md);
	}
	
	@Override
	protected void verifyOriginalDocuments(SignedDocumentValidator validator, DiagnosticData diagnosticData) {
		List<String> signatureIdList = diagnosticData.getSignatureIdList();
		for (String signatureId : signatureIdList) {
			
			SignatureWrapper signatureById = diagnosticData.getSignatureById(signatureId);
			if (signatureById.isCounterSignature()) {
				continue;
			}

			List<DSSDocument> retrievedOriginalDocuments = validator.getOriginalDocuments(signatureId);
			assertTrue(Utils.isCollectionNotEmpty(retrievedOriginalDocuments));
			
			List<DSSDocument> originalDocuments = getOriginalDocuments();
			for (DSSDocument original : originalDocuments) {
				boolean found = false;
				boolean toBeCanonicalized = MimeType.XML.equals(original.getMimeType()) || MimeType.HTML.equals(original.getMimeType());
				String originalDigest = getDigest(original, toBeCanonicalized);
				for (DSSDocument retrieved : retrievedOriginalDocuments) {
					String retrievedDigest = getDigest(retrieved, toBeCanonicalized);
					if (Utils.areStringsEqual(originalDigest, retrievedDigest)) {
						found = true;
					}
				}

				assertTrue(found, "Unable to retrieve the original document " + original.getName());
			}
		}
	}

}