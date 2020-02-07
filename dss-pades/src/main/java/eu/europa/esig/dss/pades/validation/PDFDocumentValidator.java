/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.pades.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import eu.europa.esig.dss.enumerations.TimestampLocation;
import eu.europa.esig.dss.enumerations.TimestampType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESUtils;
import eu.europa.esig.dss.pades.validation.scope.PAdESSignatureScopeFinder;
import eu.europa.esig.dss.pdf.IPdfObjFactory;
import eu.europa.esig.dss.pdf.PDFSignatureService;
import eu.europa.esig.dss.pdf.PdfDocDssRevision;
import eu.europa.esig.dss.pdf.PdfDocTimestampRevision;
import eu.europa.esig.dss.pdf.PdfDssDict;
import eu.europa.esig.dss.pdf.PdfSignatureRevision;
import eu.europa.esig.dss.pdf.ServiceLoaderPdfObjFactory;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.DiagnosticDataBuilder;
import eu.europa.esig.dss.validation.ListCRLSource;
import eu.europa.esig.dss.validation.ListCertificateSource;
import eu.europa.esig.dss.validation.ListOCSPSource;
import eu.europa.esig.dss.validation.PdfRevision;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.ValidationContext;
import eu.europa.esig.dss.validation.timestamp.TimestampToken;

/**
 * Validation of PDF document.
 */
public class PDFDocumentValidator extends SignedDocumentValidator {
	
	private static final byte[] pdfPreamble = new byte[] { '%', 'P', 'D', 'F', '-' };

	private IPdfObjFactory pdfObjectFactory = new ServiceLoaderPdfObjFactory();
	
	private List<PdfRevision> documentRevisions;

	PDFDocumentValidator() {
	}

	/**
	 * The default constructor for PDFDocumentValidator.
	 */
	public PDFDocumentValidator(final DSSDocument document) {
		super(new PAdESSignatureScopeFinder());
		this.document = document;
	}

	@Override
	public boolean isSupported(DSSDocument dssDocument) {
		return DSSUtils.compareFirstBytes(dssDocument, pdfPreamble);
	}
	
	/**
	 * Set the IPdfObjFactory. Allow to set the used implementation. Cannot be null.
	 * 
	 * @param pdfObjFactory
	 *                      the implementation to be used.
	 */
	public void setPdfObjFactory(IPdfObjFactory pdfObjFactory) {
		Objects.requireNonNull(pdfObjFactory, "PdfObjFactory is null");
		this.pdfObjectFactory = pdfObjFactory;
	}
	
	@Override
	protected DiagnosticDataBuilder prepareDiagnosticDataBuilder(final ValidationContext validationContext) {
		
		List<AdvancedSignature> allSignatures = getAllSignatures();
		List<TimestampToken> detachedTimestamps = getDetachedTimestamps();
		List<PdfDssDict> dssDictionaries = getDssDictionaries();

		ListCertificateSource listCertificateSource = mergeCertificateSource(validationContext, allSignatures, detachedTimestamps, dssDictionaries);
		ListCRLSource listCRLSource = mergeCRLSources(allSignatures, detachedTimestamps, dssDictionaries);
		ListOCSPSource listOCSPSource = mergeOCSPSources(allSignatures, detachedTimestamps, dssDictionaries);

		prepareCertificateVerifier(listCRLSource, listOCSPSource);
		
		prepareSignatureValidationContext(validationContext, allSignatures);
        prepareDetachedTimestampValidationContext(validationContext, detachedTimestamps);
		
		if (!skipValidationContextExecution) {
			validateContext(validationContext);
		}
		
		return getDiagnosticDataBuilderConfiguration(validationContext, allSignatures, listCertificateSource, listCRLSource, listOCSPSource);
	}
	
	protected ListCRLSource mergeCRLSources(Collection<AdvancedSignature> allSignatures, Collection<TimestampToken> timestampTokens, 
			Collection<PdfDssDict> dssDictionaries) {
		ListCRLSource listCRLSource = mergeCRLSources(allSignatures, timestampTokens);
		if (Utils.isCollectionNotEmpty(dssDictionaries)) {
			for (PdfDssDict dssDictionary : dssDictionaries) {
				listCRLSource.add(new PAdESCRLSource(dssDictionary));
			}
		}
		return listCRLSource;
	}
 	
	protected ListOCSPSource mergeOCSPSources(Collection<AdvancedSignature> allSignatures, Collection<TimestampToken> timestampTokens, 
			Collection<PdfDssDict> dssDictionaries) {
		ListOCSPSource listOCSPSource = mergeOCSPSources(allSignatures, timestampTokens);
		if (Utils.isCollectionNotEmpty(dssDictionaries)) {
			for (PdfDssDict dssDictionary : dssDictionaries) {
				listOCSPSource.add(new PAdESOCSPSource(dssDictionary));
			}
		}
		return listOCSPSource;
	}
	
	protected ListCertificateSource mergeCertificateSource(final ValidationContext validationContext, Collection<AdvancedSignature> allSignatures, 
			Collection<TimestampToken> timestampTokens, Collection<PdfDssDict> dssDictionaries) {
		ListCertificateSource listCertificateSource = mergeCertificateSource(validationContext, allSignatures, timestampTokens);
		if (Utils.isCollectionNotEmpty(dssDictionaries)) {
			for (PdfDssDict dssDictionary : dssDictionaries) {
				CommonCertificateSource commonCertificateSource = new CommonCertificateSource();
				for (CertificateToken certificateToken : dssDictionary.getCERTs().values()) {
					commonCertificateSource.addCertificate(certificateToken);
				}
				listCertificateSource.add(commonCertificateSource);
			}
		}
		return listCertificateSource;
	}

	@Override
	public List<AdvancedSignature> getSignatures() {
		final List<AdvancedSignature> signatures = new ArrayList<>();
		
		for (PdfRevision pdfRevision : getRevisions()) {
			if (pdfRevision instanceof PdfSignatureRevision) {
				PdfSignatureRevision pdfSignatureRevision = (PdfSignatureRevision) pdfRevision;
				try {
					final PAdESSignature padesSignature = new PAdESSignature(pdfSignatureRevision, validationCertPool, documentRevisions);
					padesSignature.setSignatureFilename(document.getName());
					padesSignature.setProvidedSigningCertificateToken(providedSigningCertificateToken);
					signatures.add(padesSignature);
					
				} catch (Exception e) {
					throw new DSSException(String.format("Unable to collect a signature. Reason : [%s]", e.getMessage()), e);
				}
				
			}
		}
		Collections.reverse(signatures);
		return signatures;
	}

	@Override
	public List<TimestampToken> getDetachedTimestamps() {
		final List<TimestampToken> timestamps = new ArrayList<>();
		
		for (PdfRevision pdfRevision : getRevisions()) {
			if (pdfRevision instanceof PdfDocTimestampRevision) {
				PdfDocTimestampRevision pdfDocTimestampRevision = (PdfDocTimestampRevision) pdfRevision;
				try {
					TimestampToken timestampToken = new TimestampToken(pdfDocTimestampRevision, 
							TimestampType.CONTENT_TIMESTAMP, validationCertPool, TimestampLocation.DOC_TIMESTAMP);
					timestampToken.setFileName(document.getName());
					timestampToken.matchData(new InMemoryDocument(pdfDocTimestampRevision.getRevisionCoveredBytes()));
					
					PAdESSignatureScopeFinder signatureScopeFinder = new PAdESSignatureScopeFinder();
					signatureScopeFinder.setDefaultDigestAlgorithm(getDefaultDigestAlgorithm());
					timestampToken.setTimestampScopes(Arrays.asList(signatureScopeFinder.findSignatureScope(pdfDocTimestampRevision)));
					
					timestamps.add(timestampToken);
						
				} catch (Exception e) {
					throw new DSSException(String.format("Unable to collect a timestamp. Reason : [%s]", e.getMessage()), e);
				}
				
			}
		}
		Collections.reverse(timestamps);
		return timestamps;
	}
	
	/**
	 * Returns a list of found DSS Dictionaries across different revisions
	 * @return list of {@link PdfDssDict}s
	 */
	public List<PdfDssDict> getDssDictionaries() {
		List<PdfDssDict> docDssRevisions = new ArrayList<>();
		
		for (PdfRevision pdfRevision : getRevisions()) {
			if (pdfRevision instanceof PdfDocDssRevision) {
				PdfDocDssRevision dssRevision = (PdfDocDssRevision) pdfRevision;
				docDssRevisions.add(dssRevision.getDssDictionary());
			}
		}
		Collections.reverse(docDssRevisions);
		return docDssRevisions;
	}
	
	protected List<PdfRevision> getRevisions() {
		if (documentRevisions == null) {
			PDFSignatureService pdfSignatureService = pdfObjectFactory.newPAdESSignatureService();
			documentRevisions = pdfSignatureService.validateSignatures(validationCertPool, document);
		}
		return documentRevisions;
	}

	@Override
	public List<DSSDocument> getOriginalDocuments(String signatureId) {
		Objects.requireNonNull(signatureId, "Signature Id cannot be null");
		List<AdvancedSignature> signatures = getSignatures();
		for (AdvancedSignature signature : signatures) {
			if (signature.getId().equals(signatureId)) {
				return getOriginalDocuments(signature);
			}
		}
		return Collections.emptyList();
	}
	
	@Override
	public List<DSSDocument> getOriginalDocuments(AdvancedSignature advancedSignature) {
		PAdESSignature padesSignature = (PAdESSignature) advancedSignature;
		List<DSSDocument> result = new ArrayList<>();
		InMemoryDocument originalPDF = PAdESUtils.getOriginalPDF(padesSignature);
		if (originalPDF != null) {
			result.add(originalPDF);
		}
		return result;
	}

}
