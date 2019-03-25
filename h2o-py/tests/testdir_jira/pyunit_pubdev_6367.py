
import h2o
import pandas as pd
from tests import pyunit_utils


def pubdev_5394():
    
    l = list(range(100))
    l.append("#")
    l.append("#")
    df = pd.DataFrame({1: l, 2: l, 3: l, 4: l, 5: l})
    df.to_csv("test.tsv", sep="\t", index=False)
    
    df = h2o.import_file("test.tsv", header=1, non_data_line_markers='#')
    print(df.nrows)
    print(len(l))




if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5394)
else:
    pubdev_5394()
