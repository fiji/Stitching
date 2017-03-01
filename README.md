[![](https://travis-ci.org/fiji/Stitching.svg?branch=master)](https://travis-ci.org/fiji/Stitching)

Stitching
=========

There is an increasing demand to image large biological specimen at high
resolution. Typically those specimen do not fit in the field of view of
the microscope. To overcome this drawback, motorized stages moving the
sample are used to create a tiled scan of the whole specimen. The physical
coordinates provided by the microscope stage are not precise enough to
allow reconstruction ("Stitching") of the whole image from individual
image stacks.

The Stitching Plugin (2d-5d) is able to reconstruct big images/stacks from
an arbitrary number of tiled input images/stacks, making use of the
Fourier Shift Theorem that computes all possible translations (x, y[, z])
between two 2d/3d images at once, yielding the best overlap in terms of
the cross correlation measure. If more than two input images/stacks are
used the correct placement of all tiles is determined using a global
optimization. The stitching is able to align an arbitrary amount of
channels and supports timelapse registration. To remove brightness
differences at the tile borders, non-linear intensity blending can be
applied.

For further details, please see the:
- documentation http://fiji.sc/Stitching
- publication: http://bioinformatics.oxfordjournals.org/content/25/11/1463.abstract
