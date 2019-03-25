package hex.genmodel.algos.xgboost;

import biz.k11i.xgboost.Predictor;
import biz.k11i.xgboost.gbm.GBTree;
import biz.k11i.xgboost.gbm.GradBooster;
import biz.k11i.xgboost.learner.ObjFunction;
import biz.k11i.xgboost.tree.RegTree;
import biz.k11i.xgboost.tree.TreeSHAPHelper;
import biz.k11i.xgboost.util.FVec;
import hex.genmodel.GenModel;
import hex.genmodel.algos.tree.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of XGBoostMojoModel that uses Pure Java Predict
 * see https://github.com/h2oai/xgboost-predictor
 */
public final class XGBoostJavaMojoModel extends XGBoostMojoModel {

  private Predictor _predictor;
  private TreeSHAPPredictor<FVec> _treeSHAPPredictor;
  private OneHotEncoderFactory _1hotFactory;

  static {
    ObjFunction.register("reg:gamma", new RegObjFunction());
    ObjFunction.register("reg:tweedie", new RegObjFunction());
    ObjFunction.register("count:poisson", new RegObjFunction());
  }

  public XGBoostJavaMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn) {
    this(boosterBytes, columns, domains, responseColumn, false);
  }

  public XGBoostJavaMojoModel(byte[] boosterBytes, String[] columns, String[][] domains, String responseColumn, 
                              boolean enableTreeSHAP) {
    super(columns, domains, responseColumn);
    _predictor = makePredictor(boosterBytes);
    _treeSHAPPredictor = enableTreeSHAP ? makeTreeSHAPPredictor(_predictor) : null;
  }

  @Override
  public void postReadInit() {
    _1hotFactory = new OneHotEncoderFactory();
  }

  private static Predictor makePredictor(byte[] boosterBytes) {
    try (InputStream is = new ByteArrayInputStream(boosterBytes)) {
      return new Predictor(is);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static TreeSHAPPredictor<FVec> makeTreeSHAPPredictor(Predictor predictor) {
    if (predictor.getNumClass() > 2) {
      throw new UnsupportedOperationException("Calculating contributions is currently not supported for multinomial models.");
    }
    GBTree gbTree = (GBTree) predictor.getBooster();
    RegTree[] trees = gbTree.getGroupedTrees()[0];
    List<TreeSHAPPredictor<FVec>> predictors = new ArrayList<>(trees.length);
    for (RegTree tree : trees) {
      predictors.add(TreeSHAPHelper.makePredictor(tree));
    }
    float initPred = TreeSHAPHelper.getInitPrediction(predictor);
    return new TreeSHAPEnsemble<>(predictors, initPred);
  }

  public final double[] score0(double[] doubles, double offset, double[] preds) {
    if (offset != 0) throw new UnsupportedOperationException("Unsupported: offset != 0");

    FVec row = _1hotFactory.fromArray(doubles);
    float[] out = _predictor.predict(row);

    return toPreds(doubles, out, preds, _nclasses, _priorClassDistrib, _defaultThreshold);
  }

  public final Object makeContributionsWorkspace() {
    return _treeSHAPPredictor.makeWorkspace();
  }

  public final float[] calculateContributions(FVec row, float[] out_contribs, Object workspace) {
    _treeSHAPPredictor.calculateContributions(row, out_contribs, 0, -1, workspace);
    return out_contribs;
  }

  static ObjFunction getObjFunction(String name) {
    return ObjFunction.fromName(name);
  }

  @Override
  public void close() {
    _predictor = null;
    _treeSHAPPredictor = null;
    _1hotFactory = null;
  }

  @Override
  public SharedTreeGraph convert(final int treeNumber, final String treeClass) {
    GradBooster booster = _predictor.getBooster();
    return _computeGraph(booster, treeNumber);
  }

  private static class RegObjFunction extends ObjFunction {
    @Override
    public float[] predTransform(float[] preds) {
      if (preds.length != 1)
        throw new IllegalStateException("Regression problem is supposed to have just a single predicted value, got " +
                preds.length + " instead.");
      preds[0] = (float) Math.exp(preds[0]);
      return preds;
    }

    @Override
    public float predTransform(float pred) {
      return (float) Math.exp(pred);
    }
  }

  private class OneHotEncoderFactory {
    private final int[] _catMap;
    private final float _notHot;

    OneHotEncoderFactory() {
      _notHot = _sparse ? Float.NaN : 0;
      if (_catOffsets == null) {
        _catMap = new int[0];
      } else {
        _catMap = new int[_catOffsets[_cats]];
        for (int c = 0; c < _cats; c++) {
          for (int j = _catOffsets[c]; j < _catOffsets[c+1]; j++)
            _catMap[j] = c;
        }
      }
    }

    OneHotEncoderFVec fromArray(double[] input) {
      float[] numValues = new float[_nums];
      int[] catValues = new int[_cats];
      GenModel.setCats(input, catValues, _cats, _catOffsets, _useAllFactorLevels);
      for (int i = 0; i < numValues.length; i++) {
        float val = (float) input[_cats + i];
        numValues[i] = _sparse && (val == 0) ? Float.NaN : val;
      }

      return new OneHotEncoderFVec(_catMap, catValues, numValues, _notHot);
    }
  }

  private class OneHotEncoderFVec implements FVec {
    private final int[] _catMap;
    private final int[] _catValues;
    private final float[] _numValues;
    private final float _notHot;

    private  OneHotEncoderFVec(int[] catMap, int[] catValues, float[] numValues, float notHot) {
      _catMap = catMap;
      _catValues = catValues;
      _numValues = numValues;
      _notHot = notHot;
    }

    @Override
    public final float fvalue(int index) {
      if (index >= _catMap.length)
        return _numValues[index - _catMap.length];

      final boolean isHot = _catValues[_catMap[index]] == index;
      return isHot ? 1 : _notHot;
    }
  }

}
