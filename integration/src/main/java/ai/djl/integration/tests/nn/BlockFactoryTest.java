/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package ai.djl.integration.tests.nn;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.nn.BlockFactory;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.SymbolBlock;
import ai.djl.nn.core.Linear;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ModelNotFoundException;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.testing.Assertions;
import ai.djl.training.ParameterStore;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslateException;
import ai.djl.util.Utils;
import ai.djl.util.ZipUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BlockFactoryTest {

    @Test
    public void testBlockLoadingSaving()
            throws IOException, ModelNotFoundException, MalformedModelException,
                    TranslateException {
        TestBlockFactory factory = new TestBlockFactory();
        Model model = factory.getRemoveLastBlockModel();
        try (NDManager manager = NDManager.newBaseManager()) {
            Block block = model.getBlock();
            block.forward(
                    new ParameterStore(manager, true),
                    new NDList(manager.ones(new Shape(1, 3, 32, 32))),
                    true);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            block.saveParameters(new DataOutputStream(os));
            ByteArrayInputStream bis = new ByteArrayInputStream(os.toByteArray());
            Block newBlock = factory.newBlock(manager);
            newBlock.loadParameters(manager, new DataInputStream(bis));
            try (Model test = Model.newInstance("test")) {
                test.setBlock(newBlock);
                try (Predictor<NDList, NDList> predOrigin =
                                model.newPredictor(new NoopTranslator());
                        Predictor<NDList, NDList> predDest =
                                test.newPredictor(new NoopTranslator())) {
                    NDList input = new NDList(manager.ones(new Shape(1, 3, 32, 32)));
                    NDList originOut = predOrigin.predict(input);
                    NDList destOut = predDest.predict(input);
                    Assertions.assertAlmostEquals(originOut, destOut);
                }
            }
        }
        model.close();
    }

    @Test
    public void testBlockFactoryLoadingFromZip()
            throws MalformedModelException, ModelNotFoundException, IOException,
                    TranslateException {
        Path savedDir = Paths.get("build/testBlockFactory");
        Utils.deleteQuietly(savedDir);
        Path zipPath = prepareModel(savedDir);
        // load model from here
        Criteria<NDList, NDList> criteria =
                Criteria.builder()
                        .setTypes(NDList.class, NDList.class)
                        .optModelPath(zipPath)
                        .optModelName("exported")
                        .build();
        try (NDManager manager = NDManager.newBaseManager();
                ZooModel<NDList, NDList> model = criteria.loadModel();
                Predictor<NDList, NDList> pred = model.newPredictor()) {
            NDList destOut = pred.predict(new NDList(manager.ones(new Shape(1, 3, 32, 32))));
            Assert.assertEquals(destOut.singletonOrThrow().getShape(), new Shape(1, 10));
        }
    }

    private Path prepareModel(Path savedDir)
            throws IOException, ModelNotFoundException, MalformedModelException {
        TestBlockFactory factory = new TestBlockFactory();
        Model model = factory.getRemoveLastBlockModel();
        try (NDManager manager = NDManager.newBaseManager()) {
            Block block = model.getBlock();
            block.forward(
                    new ParameterStore(manager, true),
                    new NDList(manager.ones(new Shape(1, 3, 32, 32))),
                    true);
            model.save(savedDir, "exported");
        }
        model.close();
        Path classDir = savedDir.resolve("classes/ai/djl/integration/tests/nn");
        Files.createDirectories(classDir);
        Files.copy(
                Paths.get(
                        "build/classes/java/main/ai/djl/integration/tests/nn/BlockFactoryTest$TestBlockFactory.class"),
                classDir.resolve("BlockFactoryTest$TestBlockFactory.class"));
        Path zipPath =
                Paths.get("build/testBlockFactory" + Engine.getInstance().getEngineName() + ".zip");
        Files.deleteIfExists(zipPath);
        ZipUtils.zip(savedDir, zipPath, false);
        return zipPath;
    }

    public static class TestBlockFactory implements BlockFactory {

        private static final long serialVersionUID = 1234567L;

        @Override
        public Block newBlock(NDManager manager) {
            SequentialBlock newBlock = new SequentialBlock();
            newBlock.add(SymbolBlock.newInstance(manager));
            newBlock.add(Linear.builder().setUnits(10).build());
            return newBlock;
        }

        public Model getRemoveLastBlockModel()
                throws MalformedModelException, ModelNotFoundException, IOException {
            String name = Engine.getInstance().getEngineName();
            Criteria.Builder<Image, Classifications> builder =
                    Criteria.builder()
                            .optApplication(Application.CV.IMAGE_CLASSIFICATION)
                            .setTypes(Image.class, Classifications.class)
                            .optProgress(new ProgressBar())
                            .optArtifactId("resnet")
                            .optEngine(name)
                            .optGroupId("ai.djl." + name.toLowerCase())
                            .optFilter("layers", "50");
            Model model = builder.build().loadModel();
            SequentialBlock newBlock = new SequentialBlock();
            SymbolBlock block = (SymbolBlock) model.getBlock();
            newBlock.add(block);
            newBlock.add(Linear.builder().setUnits(10).build());
            model.setBlock(newBlock);
            return model;
        }
    }
}
