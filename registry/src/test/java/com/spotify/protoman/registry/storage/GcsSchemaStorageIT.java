/*-
 * -\-\-
 * protoman-registry
 * --
 * Copyright (C) 2016 - 2018 Spotify AB
 * --
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
 * -/-/-
 */

package com.spotify.protoman.registry.storage;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.spotify.protoman.registry.SchemaFile;
import com.spotify.protoman.registry.SchemaVersion;
import com.spotify.protoman.registry.storage.SchemaStorage.ReadAndWriteTransaction;
import com.spotify.protoman.registry.storage.SchemaStorage.ReadOnlyTransaction;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * NOTE:
 * This test requires a versioned GCS bucket called protoman-integration-tests to exist
 * and for versioning to be enabled on that bucket, or the test will be ignored
 */
public class GcsSchemaStorageIT extends GcsBucketIntegrationTestBase {

  private static final String BUCKET = "protoman-integration-tests";

  private GcsSchemaStorage schemaStorage;

  private static final SchemaFile schemaFile1 = SchemaFile.create(
      Paths.get("pkg1/proto1.proto"), "CONTENT1");

  private static final SchemaFile schemaFile2 = SchemaFile.create(
      Paths.get("pkg2/proto2.proto"), "CONTENT2");

  private static final SchemaVersion VERSION_1_0_0 = SchemaVersion.create("1", 0, 0);
  private static final SchemaVersion VERSION_2_0_0 = SchemaVersion.create("2", 0, 0);
  private static final SchemaVersion VERSION_0_1_0 = SchemaVersion.create("0", 1, 0);

  public GcsSchemaStorageIT() {
    super(StorageOptions.getDefaultInstance().getService(),
        BUCKET,
        true);
  }

  @Before
  public void setup() {
    schemaStorage = GcsSchemaStorage.create(storage, BUCKET);
  }

  @After
  public void tearDown() {
    if (schemaStorage != null) {
      storage.list(BUCKET, Storage.BlobListOption.prefix("protos/"))
          .iterateAll().forEach(blob -> storage.delete(blob.getBlobId()));
      storage.delete(BlobId.of(BUCKET, "index.pb"));
    }
  }

  @Test
  public void testTransactions() {

    final long snapshot0 = schemaStorage.getLatestSnapshotVersion();

    // add file1
    final long snapshot1;
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.storeFile(schemaFile1);
      snapshot1 = tx.commit();
    }

    // add package version for pkg1
    final long snapshot2;
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.storePackageVersion("pkg1", VERSION_1_0_0);
      snapshot2 = tx.commit();
    }

    // add file2 and change package version for pkg1 and add package version for pkg2
    final long snapshot3;
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.storeFile(schemaFile2);
      tx.storePackageVersion("pkg1", VERSION_2_0_0);
      tx.storePackageVersion("pkg2", VERSION_0_1_0);
      snapshot3 = tx.commit();
    }

    // delete a file
    final long snapshot4;
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.deleteFile(schemaFile2.path());
      snapshot4 = tx.commit();
    }

    // delete a file and update package version without committing transaction
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.storeFile(schemaFile2);
      tx.storePackageVersion("pkg1", VERSION_2_0_0);
      tx.storePackageVersion("pkg2", VERSION_0_1_0);
      // no commit
    }

    final long snapshot5 = schemaStorage.getLatestSnapshotVersion();

    assertThat(schemaFiles(snapshot0), equalTo(ImmutableSet.of()));
    assertThat(packageVersions(snapshot0), equalTo(ImmutableMap.of()));

    assertThat(snapshot1, is(greaterThan(snapshot0)));
    assertThat(schemaFiles(snapshot1), equalTo(ImmutableSet.of(schemaFile1)));
    assertThat(packageVersions(snapshot1), equalTo(ImmutableMap.of()));

    assertThat(snapshot2, is(greaterThan(snapshot1)));
    assertThat(schemaFiles(snapshot2), equalTo(ImmutableSet.of(schemaFile1)));
    assertThat(packageVersions(snapshot2), equalTo(ImmutableMap.of(
        "pkg1", VERSION_1_0_0)));

    assertThat(snapshot3, is(greaterThan(snapshot2)));
    assertThat(schemaFiles(snapshot3), equalTo(ImmutableSet.of(schemaFile1, schemaFile2)));
    assertThat(packageVersions(snapshot3), equalTo(ImmutableMap.of(
        "pkg1", VERSION_2_0_0,
        "pkg2", VERSION_0_1_0)));

    assertThat(snapshot4, is(greaterThan(snapshot3)));
    assertThat(schemaFiles(snapshot4), equalTo(ImmutableSet.of(schemaFile1)));
    assertThat(packageVersions(snapshot4), equalTo(ImmutableMap.of(
        "pkg1", VERSION_2_0_0,
        "pkg2", VERSION_0_1_0)));

    assertThat(snapshot5, equalTo(snapshot4));
    assertThat(schemaFiles(snapshot4), equalTo(ImmutableSet.of(schemaFile1)));
    assertThat(packageVersions(snapshot4), equalTo(ImmutableMap.of(
        "pkg1", VERSION_2_0_0,
        "pkg2", VERSION_0_1_0)));

  }

  @Test
  public void testPackageVersion() {
    final Optional<SchemaVersion> before;
    final Optional<SchemaVersion> after;

    try (final ReadOnlyTransaction tx = schemaStorage.open()) {
      before = tx.getPackageVersion("pkg1");
    }

    // store pkg
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.storePackageVersion("pkg1", SchemaVersion.create("1", 0, 0));
      tx.commit();
    }

    try (final ReadOnlyTransaction tx = schemaStorage.open()) {
      after = tx.getPackageVersion("pkg1");
    }

    assertThat(before, is(Optional.empty()));
    assertThat(after, is(Optional.of(SchemaVersion.create("1", 0, 0))));
  }

  @Test(expected = IllegalStateException.class)
  public void useOfAlreadyCommittedTransactionThrows_get() {
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.commit();
      tx.storeProtoDependencies(
          Paths.get("foo.proto"),
          ImmutableSet.of(Paths.get("bar.proto"))
      );
    }
  }

  @Test(expected = IllegalStateException.class)
  public void useOfAlreadyCommittedTransactionThrows_commit() {
    try (final ReadAndWriteTransaction tx = schemaStorage.open()) {
      tx.commit();
      tx.commit();
    }
  }

  private Set<SchemaFile> schemaFiles(long snapshotVersion) {
    final Set<SchemaFile> all;
    try (final ReadOnlyTransaction tx = schemaStorage.open(snapshotVersion)) {
      all = tx.fetchAllFiles().collect(Collectors.toSet());
    }
    return all;
  }

  private Map<String, SchemaVersion> packageVersions(long snapshotVersion) {
    final Map<String, SchemaVersion> all;
    try (final ReadOnlyTransaction tx = schemaStorage.open(snapshotVersion)) {
      all = tx.allPackageVersions();
    }
    return all;
  }
}