import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import loci.common.DataTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatWriter;
import loci.formats.ImageWriter;
import loci.formats.MetadataTools;
import loci.formats.gui.AWTImageTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;
import ome.units.quantity.Length;
import ome.units.quantity.Time;
import ome.units.unit.Unit;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.EnumerationException;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.PositiveInteger;

public class Problem {

	public static void main(String[] args) 
			throws FormatException, IOException, DependencyException, 
					ServiceException, EnumerationException {

	  /** the underlying ImagePlus object */
		ImagePlus imp;

	  String filename = "test.tif";
	  
		// create new ImagePlus
		imp = NewImage.createByteImage("Title", 
				1, 1, 1, NewImage.FILL_BLACK);
		imp.setIgnoreFlush(true);
		imp.setDimensions(1, 1, 1);
		imp.setOpenAsHyperStack(false);
		
		int ptype = 0;
		int channels = 1;
		
		switch (imp.getType()) {
		case ImagePlus.GRAY8:
		case ImagePlus.COLOR_256:
			ptype = FormatTools.UINT8;
			break;
		case ImagePlus.COLOR_RGB:
			channels = 3;
			ptype = FormatTools.UINT8;
			break;
		case ImagePlus.GRAY16:
			ptype = FormatTools.UINT16;
			break;
		case ImagePlus.GRAY32:
			ptype = FormatTools.FLOAT;
			break;
		}

		// image title
		String title = imp.getTitle();

		// new image writer
		IFormatWriter w = new ImageWriter().getWriter(filename);
		FileInfo fi = imp.getOriginalFileInfo();
		String xml = fi == null ? null : fi.description == null ? null :
			fi.description.indexOf("xml") == -1 ? null : fi.description;

		
		// ------- prepare the OME XML meta data object ---------
		
		// create a OME XML meta data store
		ServiceFactory factory = new ServiceFactory();
		OMEXMLService service = factory.getInstance(OMEXMLService.class);
		IMetadata store = service.createOMEXMLMetadata(xml);

		if (xml == null) {
			store.createRoot();
		}
		else if (store.getImageCount() > 1) {
			// the original dataset had multiple series
			// we need to modify the IMetadata to represent the correct series
			// (a series is one microscopy imaging run, which can be stored with others in one experiment dataset, see e.g. lif-files) 
			ArrayList<Integer> matchingSeries = new ArrayList<Integer>();
			for (int series=0; series<store.getImageCount(); series++) {
				String type = store.getPixelsType(series).toString();
				int pixelType = FormatTools.pixelTypeFromString(type);
				if (pixelType == ptype) {
					String imageName = store.getImageName(series);
					if (title.indexOf(imageName) >= 0) {
						matchingSeries.add(series);
					}
				}
			}

			int series = 0;
			if (matchingSeries.size() > 1) {
				for (int i=0; i<matchingSeries.size(); i++) {
					int index = matchingSeries.get(i);
					String name = store.getImageName(index);
					boolean valid = true;
					for (int j=0; j<matchingSeries.size(); j++) {
						if (i != j) {
							String compName = store.getImageName(matchingSeries.get(j));
							if (compName.indexOf(name) >= 0) {
								valid = false;
								break;
							}
						}
					}
					if (valid) {
						series = index;
						break;
					}
				}
			}
			else if (matchingSeries.size() == 1) 
				series = matchingSeries.get(0);

			// remove all non-matching series entries from the OME XML meta data object
			OMEXMLMetadataRoot root = (OMEXMLMetadataRoot)store.getRoot();
			ome.xml.model.Image exportImage = root.getImage(series);
			List<ome.xml.model.Image> allImages = root.copyImageList();
			for (ome.xml.model.Image img : allImages) {
				if (!img.equals(exportImage)) {
					root.removeImage(img);
				}
			}
			store.setRoot(root);
		}

		// set image dimensions
		store.setPixelsSizeX(new PositiveInteger(imp.getWidth()), 0);
		store.setPixelsSizeY(new PositiveInteger(imp.getHeight()), 0);
		store.setPixelsSizeZ(new PositiveInteger(imp.getNSlices()), 0);
		store.setPixelsSizeC(new PositiveInteger(channels*imp.getNChannels()), 0);
		store.setPixelsSizeT(new PositiveInteger(imp.getNFrames()), 0);

		
		if (store.getImageName(0) == null) {
			store.setImageName(title, 0);
		}
		
		if (store.getImageID(0) == null) {
			store.setImageID(MetadataTools.createLSID("Image", 0), 0);
		}
		
		if (store.getPixelsID(0) == null) {
			store.setPixelsID(MetadataTools.createLSID("Pixels", 0), 0);
		}

		if (store.getPixelsType(0) == null) {
			store.setPixelsType(PixelType.fromString(
					FormatTools.getPixelTypeString(ptype)), 0);
		}
		
		if (store.getPixelsBinDataCount(0) == 0 ||
				store.getPixelsBinDataBigEndian(0, 0) == null) {
			store.setPixelsBinDataBigEndian(Boolean.FALSE, 0, 0);
		}
		
		if (store.getPixelsDimensionOrder(0) == null) {
				store.setPixelsDimensionOrder(DimensionOrder.XYCZT, 0);
		}

		String[] labels = imp.getStack().getSliceLabels();
		for (int c=0; c<imp.getNChannels(); c++) {
	
			if (c >= store.getChannelCount(0) || store.getChannelID(0, c) == null) {
				
				String lsid = MetadataTools.createLSID("Channel", 0, c);

				store.setChannelID(lsid, 0, c);
			}
			
			if (c >= store.getChannelCount(0) || store.getChannelName(0, c) == null) {
				if (labels != null && labels[c] != null) {
					store.setChannelName(labels[c], 0, c);
				}
			}
			
			store.setChannelSamplesPerPixel(new PositiveInteger(channels), 0, c);
		}

		Calibration cal = imp.getCalibration();


		physicalPixelSize_to_OME(cal, store, 0);
		

		if (imp.getImageStackSize() !=
					imp.getNChannels() * imp.getNSlices() * imp.getNFrames()) {
			
			System.err.println("ImageWriterMTB.writeImagePlus(..): " +
					"The number of slices in the stack (" + imp.getImageStackSize() +
					") does not match the number of expected slices (" +
					(imp.getNChannels() * imp.getNSlices() * imp.getNFrames()) + ")." +
					"\nOnly " + imp.getImageStackSize() +
					" planes will be exported.");
			store.setPixelsSizeZ(new PositiveInteger(imp.getImageStackSize()), 0);
			store.setPixelsSizeC(new PositiveInteger(1), 0);
			store.setPixelsSizeT(new PositiveInteger(1), 0);
		}

		// ------- configure the writer ---------
		
		// hand the meta data object to the writer
		w.setMetadataRetrieve(store);

		// set filename
		w.setId(filename);

		// test if the output pixel type is supported by the writer
		ImageProcessor proc = imp.getImageStack().getProcessor(1);
		Image firstImage = proc.createImage();
		firstImage = AWTImageTools.makeBuffered(firstImage, proc.getColorModel());
		int thisType = AWTImageTools.getPixelType((BufferedImage) firstImage);
		if (proc instanceof ColorProcessor) {
			thisType = FormatTools.UINT8;
		}

		if (!proc.isDefaultLut()) {
			w.setColorModel(proc.getColorModel());
		}

		boolean notSupportedType = !w.isSupportedType(thisType);
		if (notSupportedType) {
			throw new IllegalArgumentException("ImageWriterMTB.writeImagePlus(..): Pixel type '" 
					+ FormatTools.getPixelTypeString(thisType) + "' not supported by this format.");
		}
		
		// convert and save slices
		int size = imp.getImageStackSize();
		ImageStack is = imp.getImageStack();
		
		boolean doStack = w.canDoStacks() && size > 1;
		int start = doStack ? 0 : imp.getCurrentSlice() - 1;
		int end = doStack ? size : start + 1;

		boolean littleEndian =
			!w.getMetadataRetrieve().getPixelsBinDataBigEndian(0, 0).booleanValue();
		byte[] plane = null;
		w.setInterleaved(false);
		
		boolean verbose = true;
		
		if (verbose) {
			System.out.println(imgWriteInfo(filename, w, 0));
		}
		
		int no = 0;
		for (int i=start; i<end; i++) {
			proc = is.getProcessor(i + 1);

			int x = proc.getWidth();
			int y = proc.getHeight();

			if (proc instanceof ByteProcessor) {
				plane = (byte[]) proc.getPixels();
			}
			else if (proc instanceof ShortProcessor) {
				plane = DataTools.shortsToBytes(
						(short[]) proc.getPixels(), littleEndian);
			}
			else if (proc instanceof FloatProcessor) {
				plane = DataTools.floatsToBytes(
						(float[]) proc.getPixels(), littleEndian);
			}
			else if (proc instanceof ColorProcessor) {
				byte[][] pix = new byte[3][x*y];
				((ColorProcessor) proc).getRGB(pix[0], pix[1], pix[2]);
				plane = new byte[3 * x * y];
				System.arraycopy(pix[0], 0, plane, 0, x * y);
				System.arraycopy(pix[1], 0, plane, x * y, x * y);
				System.arraycopy(pix[2], 0, plane, 2 * x * y, x * y);
			}

			w.saveBytes(no++, plane);
		}
		w.close();

		System.out.println("Done!");
	}	

	/**
	 * Set OME meta data for image of index <code>imageIdx</code> using 
	 * information from a <code>Calibration</code> object
	 * @param cal
	 * @param omemeta
	 * @param imageIdx
	 */
	public static void physicalPixelSize_to_OME(Calibration cal, 
			IMetadata omemeta, int imageIdx) {

		Unit<Length> ul;
		if (toMicrons(cal.pixelWidth, cal.getXUnit()) > 0.0) {
			ul = Unit.CreateBaseUnit(cal.getXUnit(), cal.getXUnit());
			omemeta.setPixelsPhysicalSizeX(	
					new Length(	new Double(
							toMicrons(cal.pixelWidth, cal.getXUnit())),
							ul), 
					imageIdx);
		}

		if (toMicrons(cal.pixelHeight, cal.getYUnit()) > 0.0) {
			ul = Unit.CreateBaseUnit(cal.getYUnit(), cal.getYUnit());
			omemeta.setPixelsPhysicalSizeY(	
					new Length(	new Double(
							toMicrons(cal.pixelHeight, cal.getYUnit())),
							ul), 
					imageIdx);
		}

		if (toMicrons(cal.pixelDepth, cal.getZUnit()) > 0.0) {
			ul = Unit.CreateBaseUnit(cal.getZUnit(), cal.getZUnit());
			omemeta.setPixelsPhysicalSizeZ(	
					new Length(	new Double(
							toMicrons(cal.pixelDepth, cal.getZUnit())),
							ul), 
					imageIdx);
		}

		Unit<Time> ut = Unit.CreateBaseUnit(cal.getTimeUnit(), "s");
		Time t = new Time(new Double(
				toSeconds(cal.frameInterval, cal.getTimeUnit())), ut);
		omemeta.setPixelsTimeIncrement(t, imageIdx);		
	}

	public static String imgWriteInfo(String filename, IFormatWriter w, int imgIdx) {
		MetadataRetrieve meta = w.getMetadataRetrieve();

		String s = "Configuration of image writer:\n";
		s += "--ImageWriter-- \n";
		s += "| Output file: " + filename + "\n";
		s += "| File format: " + w.getFormat() + "\n";
		s += "| Compression: " + w.getCompression() + "\n";
		s += "| Interleaved pixel order: " + w.isInterleaved() + "\n";
		s += "| Stacks supported: " + w.canDoStacks() + "\n";
		s += "--Image--\n";
		s += "| Image title: " + meta.getImageName(imgIdx) + "\n";
		s += "| Size in x: " + meta.getPixelsSizeX(imgIdx) + "\n";
		s += "| Size in y: " + meta.getPixelsSizeY(imgIdx) + "\n";
		s += "| Size in z: " + meta.getPixelsSizeZ(imgIdx) + "\n";
		s += "| Number of (time)frames: " + meta.getPixelsSizeT(imgIdx) + "\n";
		s += "| Number of channels: " + meta.getPixelsSizeC(imgIdx).getValue()/meta.getChannelSamplesPerPixel(imgIdx, 0).getValue() + "\n";
		s += "| Samples per pixel: " + meta.getChannelSamplesPerPixel(imgIdx, 0) + "\n";
		s += "| Data type: " + meta.getPixelsType(imgIdx);
		return s;
	}

	/**
	 * Convert a value of given time unit to seconds.
	 * @param val
	 * @param unit
	 * @return
	 */
	public static double toSeconds(double val, String unit) {
		if (unit.equalsIgnoreCase("sec") || unit.equalsIgnoreCase("s") 
				|| unit.equalsIgnoreCase("second") || unit.equalsIgnoreCase("seconds"))
			return val;
		else if (unit.equalsIgnoreCase("psec") || unit.equalsIgnoreCase("ps"))
			return val * 0.000000001;
		else if (unit.equalsIgnoreCase("nsec") || unit.equalsIgnoreCase("ns"))
			return val * 0.000001;
		else if (unit.equalsIgnoreCase("msec") || unit.equalsIgnoreCase("ms"))
			return val * 0.001;
		else if (unit.equalsIgnoreCase("min") || unit.equalsIgnoreCase("m"))
			return val * 60.0;
		else if (unit.equalsIgnoreCase("hour") || unit.equalsIgnoreCase("h") || unit.equalsIgnoreCase("std"))
			return val * 360.0;
		else
			return 0.0;
	}


	/**
	 * Convert a value of given space unit to microns.
	 * @param val
	 * @param unit
	 * @return
	 */
	public static double toMicrons(double val, String unit) {
		if (unit.equalsIgnoreCase("micron") || unit.equalsIgnoreCase("microns") || unit.equalsIgnoreCase("um") || unit.equalsIgnoreCase("micrometer"))
			return val;
		else if (unit.equalsIgnoreCase("pm") || unit.equalsIgnoreCase("picometer"))
			return val * 0.000001;
		else if (unit.equalsIgnoreCase("nm") || unit.equalsIgnoreCase("nanometer"))
			return val * 0.001;
		else if (unit.equalsIgnoreCase("mm") || unit.equalsIgnoreCase("millimeter"))
			return val * 1000;
		else if (unit.equalsIgnoreCase("cm") || unit.equalsIgnoreCase("centimeter"))
			return val * 10000;
		else if (unit.equalsIgnoreCase("dm") || unit.equalsIgnoreCase("decimeter"))
			return val * 100000;
		else if (unit.equalsIgnoreCase("m") || unit.equalsIgnoreCase("meter"))
			return val * 1000000;
		else if (unit.equalsIgnoreCase("km") || unit.equalsIgnoreCase("kilometer"))
			return val * 1000000000;
		else if (unit.equalsIgnoreCase("pixel") || unit.equalsIgnoreCase("pixels"))
			return 0.0;
		else
			return -1.0;
	}
}
