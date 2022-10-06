/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;

import static org.junit.Assert.*;

public class CampaignZipBundleHandlerTests extends BaseTest {

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void setup() {
		super.beforeEach();
	}

	@After
	public void after() {
		super.afterEach();
	}

	@Test
	public void getMetadataReturnsValidInstance_When_ValidMetaFileWithNoETagExists() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		FileOutputStream fileOutputStream = new FileOutputStream(metaFile);
		fileOutputStream.write("23123123|1234|".getBytes("UTF-8"));
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertEquals(23123123, metadata.getLastModifiedDate());
		assertEquals(1234, metadata.getSize());
		assertNull(metadata.getETag());
	}

	@Test
	public void getMetadataReturnsValidInstance_When_ValidMetaFileWithETagExists() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		FileOutputStream fileOutputStream = new FileOutputStream(metaFile);
		fileOutputStream.write("23123123|1234|\"ABCDE-12345\"|".getBytes("UTF-8"));
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertEquals(23123123, metadata.getLastModifiedDate());
		assertEquals(1234, metadata.getSize());
		assertEquals("\"ABCDE-12345\"", metadata.getETag());
	}

	@Test
	public void getMetadataReturnsValidInstance_When_ValidMetaFileWithWeakETagExists() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		FileOutputStream fileOutputStream = new FileOutputStream(metaFile);
		fileOutputStream.write("23123123|1234|W/\"ABCDE-12345\"|".getBytes("UTF-8"));
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertEquals(23123123, metadata.getLastModifiedDate());
		assertEquals(1234, metadata.getSize());
		assertEquals("W/\"ABCDE-12345\"", metadata.getETag());
	}

	@Test
	public void getMetadataReturnsNullInstance_When_MetadataFormatIsIncorrect() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		FileOutputStream fileOutputStream = new FileOutputStream(metaFile);
		fileOutputStream.write("23123123/1234/".getBytes("UTF-8"));
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertNull(metadata);
	}

	@Test
	public void getMetadataReturnsNullInstance_When_MetadataHasNonNumericDate() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		FileOutputStream fileOutputStream = new FileOutputStream(metaFile);
		fileOutputStream.write("invaliddate|1234|".getBytes("UTF-8"));
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertNull(metadata);
	}

	@Test
	public void getMetadataReturnsNullInstance_When_MetadataHasNonNumericSize() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		FileOutputStream fileOutputStream = new FileOutputStream(metaFile);
		fileOutputStream.write("11322234|invalidsize|".getBytes("UTF-8"));
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertNull(metadata);
	}

	@Test
	public void getMetadataReturnsNullInstance_When_MetadataFileIsEmpty() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a valid meta content
		File metaFile = new File(temporaryFolder.getRoot(), "meta.txt");
		assertEquals(true, metaFile.createNewFile());
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertNull(metadata);
	}

	@Test
	public void getMetadataReturnsNull_When_ValidMetaFileDoesNotExists() throws Exception {
		//Setup
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//test
		CampaignRulesRemoteDownloader.Metadata metadata = zipBundleHandler.getMetadata(temporaryFolder.getRoot());
		//verify
		assertNull(metadata);
	}

	@Test
	public void processBundleWithoutETagExtractsAndWriteMeta_When_ExtractionSuccessful() throws Exception {
		//Setup
		platformServices.mockCompressedFileService.extractReturnValue = true;
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a fake zip bundle
		String somedownloadedbundlethatiszipped = "somedownloadedbundlethatiszipped";
		String metaSeparator = "|";

		File downloadedBundle = new File(temporaryFolder.getRoot(), "downloaded.bundle");
		FileOutputStream fileOutputStream = new FileOutputStream(downloadedBundle);
		fileOutputStream.write(somedownloadedbundlethatiszipped.getBytes("UTF-8"));

		File extractedOutputDir =  temporaryFolder.newFolder("output");
		//test
		boolean processBundleResult = zipBundleHandler.processDownloadedBundle(downloadedBundle,
									  extractedOutputDir.getAbsolutePath(),
									  123456, null);
		//verify
		assertTrue(processBundleResult);

		File[] files = extractedOutputDir.listFiles();
		assertNotNull(files);
		assertTrue(files.length > 0);
		assertEquals("meta.txt", files[0].getName());

		FileReader fileReader = new FileReader(files[0]);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line = bufferedReader.readLine();
		assertEquals(123456 + metaSeparator + somedownloadedbundlethatiszipped.length() + metaSeparator, line);

		assertFalse(downloadedBundle.exists());

	}

	@Test
	public void processBundleWithETagExtractsAndWriteMeta_When_ExtractionSuccessful() throws Exception {
		//Setup
		platformServices.mockCompressedFileService.extractReturnValue = true;
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a fake zip bundle
		String somedownloadedbundlethatiszipped = "somedownloadedbundlethatiszipped";
		String metaSeparator = "|";

		// create ETag
		String ETag = "\"ABCDE-12345\"";

		File downloadedBundle = new File(temporaryFolder.getRoot(), "downloaded.bundle");
		FileOutputStream fileOutputStream = new FileOutputStream(downloadedBundle);
		fileOutputStream.write(somedownloadedbundlethatiszipped.getBytes("UTF-8"));

		File extractedOutputDir =  temporaryFolder.newFolder("output");
		//test
		boolean processBundleResult = zipBundleHandler.processDownloadedBundle(downloadedBundle,
									  extractedOutputDir.getAbsolutePath(),
									  123456, ETag);
		//verify
		assertTrue(processBundleResult);

		File[] files = extractedOutputDir.listFiles();
		assertNotNull(files);
		assertTrue(files.length > 0);
		assertEquals("meta.txt", files[0].getName());

		FileReader fileReader = new FileReader(files[0]);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line = bufferedReader.readLine();
		assertEquals(123456 + metaSeparator + somedownloadedbundlethatiszipped.length() + metaSeparator + ETag +
					 metaSeparator, line);

		assertFalse(downloadedBundle.exists());

	}

	@Test
	public void processBundleWithWeakETagExtractsAndWriteMeta_When_ExtractionSuccessful() throws Exception {
		//Setup
		platformServices.mockCompressedFileService.extractReturnValue = true;
		CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(platformServices.mockCompressedFileService);
		//write a fake zip bundle
		String somedownloadedbundlethatiszipped = "somedownloadedbundlethatiszipped";
		String metaSeparator = "|";

		// create weak ETag
		String weakETag = "W/\"ABCDE-12345\"";

		File downloadedBundle = new File(temporaryFolder.getRoot(), "downloaded.bundle");
		FileOutputStream fileOutputStream = new FileOutputStream(downloadedBundle);
		fileOutputStream.write(somedownloadedbundlethatiszipped.getBytes("UTF-8"));

		File extractedOutputDir =  temporaryFolder.newFolder("output");
		//test
		boolean processBundleResult = zipBundleHandler.processDownloadedBundle(downloadedBundle,
									  extractedOutputDir.getAbsolutePath(),
									  123456, weakETag);
		//verify
		assertTrue(processBundleResult);

		File[] files = extractedOutputDir.listFiles();
		assertNotNull(files);
		assertTrue(files.length > 0);
		assertEquals("meta.txt", files[0].getName());

		FileReader fileReader = new FileReader(files[0]);
		BufferedReader bufferedReader = new BufferedReader(fileReader);
		String line = bufferedReader.readLine();
		assertEquals(123456 + metaSeparator + somedownloadedbundlethatiszipped.length() + metaSeparator + weakETag +
					 metaSeparator, line);

		assertFalse(downloadedBundle.exists());

	}

	@Test
	public void processBundleDoesNotWriteToOutputDir_When_ExtractionFails() throws Exception {
		//Setup
		platformServices.mockCompressedFileService.extractReturnValue = false;
		final CampaignZipBundleHandler zipBundleHandler = new CampaignZipBundleHandler(
			platformServices.mockCompressedFileService);
		//write a fake zip bundle
		final String downloadedBundleIdentifier = "somedownloadedbundlethatiszipped";

		File downloadedBundle = new File(temporaryFolder.getRoot(), "downloaded.bundle");
		FileOutputStream fileOutputStream = new FileOutputStream(downloadedBundle);
		fileOutputStream.write(downloadedBundleIdentifier.getBytes("UTF-8"));

		File extractedOutputDir =  temporaryFolder.newFolder("output");
		//test
		boolean processBundleResult = zipBundleHandler.processDownloadedBundle(downloadedBundle,
									  extractedOutputDir.getAbsolutePath(),
									  123456, null);
		//verify
		assertFalse(processBundleResult);

		File[] files = extractedOutputDir.listFiles();
		assertNotNull(files);
		assertEquals(0, files.length);

		assertFalse(downloadedBundle.exists());

	}
}