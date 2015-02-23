/* Name: Alexander Ding */

import java.io.*;
import java.util.concurrent.RecursiveTask;
import java.util.stream.*;
import java.util.Arrays;
import java.util.*;

// a marker for code that you need to implement
class ImplementMe extends RuntimeException {}

// an RGB triple
class RGB {
    public int R, G, B;

    RGB(int r, int g, int b) {
	R = r;
	G = g;
	B = b;
    }

    public String toString() { return "(" + R + "," + G + "," + B + ")"; }

}

// code for creating a Gaussian filter
class Gaussian {

    protected static double gaussian(int x, int mu, double sigma) {
	return Math.exp( -(Math.pow((x-mu)/sigma,2.0))/2.0 );
    }

    public static double[][] gaussianFilter(int radius, double sigma) {
	int length = 2 * radius + 1;
	double[] hkernel = new double[length];
	for(int i=0; i < length; i++)
	    hkernel[i] = gaussian(i, radius, sigma);
	double[][] kernel2d = new double[length][length];
	double kernelsum = 0.0;
	for(int i=0; i < length; i++) {
	    for(int j=0; j < length; j++) {
		double elem = hkernel[i] * hkernel[j];
		kernelsum += elem;
		kernel2d[i][j] = elem;
	    }
	}
	for(int i=0; i < length; i++) {
	    for(int j=0; j < length; j++)
		kernel2d[i][j] /= kernelsum;
	}
	return kernel2d;
    }
}

// an object representing a single PPM image
class PPMImage {
    protected int width, height, maxColorVal;
    protected RGB[] pixels;

    PPMImage(int w, int h, int m, RGB[] p) {
	width = w;
	height = h;
	maxColorVal = m;
	pixels = p;
    }
    
	// parse a PPM file to produce a PPMImage
    public static PPMImage fromFile(String fname) throws FileNotFoundException, IOException {
	FileInputStream is = new FileInputStream(fname);
	BufferedReader br = new BufferedReader(new InputStreamReader(is));
	br.readLine(); // read the P6
	String[] dims = br.readLine().split(" "); // read width and height
	int width = Integer.parseInt(dims[0]);
	int height = Integer.parseInt(dims[1]);
	int max = Integer.parseInt(br.readLine()); // read max color value
	br.close();

	is = new FileInputStream(fname);
	    // skip the first three lines
	int newlines = 0;
	while (newlines < 3) {
	    int b = is.read();
	    if (b == 10)
		newlines++;
	}

	int MASK = 0xff;
	int numpixels = width * height;
	byte[] bytes = new byte[numpixels * 3];
        is.read(bytes);
	RGB[] pixels = new RGB[numpixels];
	for (int i = 0; i < numpixels; i++) {
	    int offset = i * 3;
	    pixels[i] = new RGB(bytes[offset] & MASK, bytes[offset+1] & MASK, bytes[offset+2] & MASK);
	}

	return new PPMImage(width, height, max, pixels);
    }

	// write a PPMImage object to a file
    public void toFile(String fname) throws IOException {
	FileOutputStream os = new FileOutputStream(fname);

	String header = "P6\n" + width + " " + height + "\n" + maxColorVal + "\n";
	os.write(header.getBytes());

	int numpixels = width * height;
	byte[] bytes = new byte[numpixels * 3];
	int i = 0;
	for (RGB rgb : pixels) {
	    bytes[i] = (byte) rgb.R;
	    bytes[i+1] = (byte) rgb.G;
	    bytes[i+2] = (byte) rgb.B;
	    i += 3;
	}
	os.write(bytes);
    }

	// implement using Java 8 Streams
    public PPMImage negate() {

	int pixelNumber = this.width*this.height;
	int max = maxColorVal;
        RGB negated[] = new RGB[pixelNumber];
	List<RGB> rgblist = new ArrayList<RGB>();
        rgblist = Arrays.stream(pixels).parallel().map(x -> new RGB(max - x.R, max - x.G, max - x.B)).collect(Collectors.toList());
	negated = rgblist.toArray(negated);
	return new PPMImage(this.width, this.height, this.maxColorVal, negated);
    }
    
	// implement using Java's Fork/Join library

    //RecursiveTask subclass for mirrorImage
    class Mirror extends RecursiveTask<RGB[]>{
	RGB pixels[];
	int index;
	int width;
	int height;
	int cutoff;

	public Mirror(RGB pixels[], int index, int width, int height){
	    this.pixels = pixels;
	    this.index = index;
	    this.width = width;
	    this.height = height;
	    this.cutoff = (width*height) / 2000;
	}

	public RGB[] compute(){
	    if(index >= height+1/2){
		return pixels;
	    }
	    Mirror mirror = new Mirror(pixels, index+1, width, height);
	    mirror.fork();
	    int v = (width+1)/2;
	    RGB placeholder;
	    int x = index*width;
	    for(int i = 0; i < v; i++){
		placeholder = pixels[x+i];
		pixels[x+i] = pixels[x + width-1-i];
		pixels[x + width-1-i] = placeholder;
	    }
	    
	    pixels = mirror.join();
	    return pixels;
	}	
    }
    
    public PPMImage mirrorImage() {
	int pixelNumber = this.width * this.height;
	RGB pixelList[] = new RGB[pixelNumber];

	Mirror mirror = new Mirror(this.pixels, 0, this.width, this.height);
	pixelList = mirror.compute();
	PPMImage newImage = new PPMImage(this.width, this.height, this.maxColorVal, pixelList);
	//throw new ImplementMe();
	return newImage;
    }

    //RecursiveTask subclass for gaussianBlur
    class Blur extends RecursiveTask<RGB>{
	RGB[] pixels;
	int width;
	int height;
	double gfilter[][];
	int radius;
	int x;
	int y;

	public Blur(RGB pixels[], int width, int height, double gfilter[][], int radius, int x, int y){
	    this.pixels = pixels;
	    this.width = width;
	    this.height = height;
	    this.gfilter = gfilter;
	    this.radius = radius;
	    this.x = x;
	    this.y = y;
	}

	public RGB compute(){
	    int filtersize = 2*radius + 1;
	    double red = 0;
	    double green = 0;
	    double blue = 0;
	    for(int j = 0; j < filtersize; j++){
		for(int i = 0; i < filtersize; i++){
		    int xcoord = x - radius + i;
		    if(xcoord < 0)
			xcoord = 0;
		    else if(xcoord >= width)
			xcoord = width-1;

		    int ycoord = y - radius + j;
		    if(ycoord < 0)
			ycoord = 0;
		    else if(ycoord >= height)
			ycoord = height-1;

		    red += gfilter[i][j] * pixels[ycoord*width + xcoord].R;
		    green += gfilter[i][j] * pixels[ycoord*width + xcoord].G;
		    blue += gfilter[i][j] * pixels[ycoord*width + xcoord].B;

		    
		}
	    }
	    Long redl = Math.round(red);
	    Long greenl = Math.round(green);
	    Long bluel = Math.round(blue);
	    
	    return new RGB(redl.intValue(),greenl.intValue(),bluel.intValue());
	    
	}
    }
    
	// implement using Java's Fork/Join library
    public PPMImage gaussianBlur(int radius, double sigma) {
	int pixelNumber = this.width*this.height;
	double gfilter[][] = Gaussian.gaussianFilter(radius, sigma);
	Blur blurArray[] = new Blur[pixelNumber];

	for(int y = 0; y < height; y++){
	    for(int x = 0; x < width; x++){
		blurArray[y*width + x] = new Blur(pixels, width, height, gfilter, radius, x, y);
		blurArray[y*width + x].fork();
	    }
	}
	RGB newPixels[] = new RGB[pixelNumber];
	for(int z = 0; z < pixelNumber; z++){
	    newPixels[z] = blurArray[z].join();
	}
	
	//throw new ImplementMe();
	return new PPMImage(this.width, this.height, this.maxColorVal, newPixels);
    }

	// implement using Java 8 Streams
    public PPMImage gaussianBlur2(int radius, double sigma) {
	
	int pixelNumber = this.width*this.height;
	int max = maxColorVal;
	double gfilter[][] = Gaussian.gaussianFilter(radius, sigma);

        RGB blurred[] = new RGB[pixelNumber];
	List<RGB> rgblist = new ArrayList<RGB>();
        rgblist = IntStream.range(0,pixelNumber).parallel().mapToObj(index -> {
		//very long lambda function
	    int filtersize = 2*radius + 1;
	    double red = 0;
	    double green = 0;
	    double blue = 0;
	    int x = index % this.width;
	    int y = index / this.width;
	    for(int j = 0; j < filtersize; j++){
		for(int i = 0; i < filtersize; i++){
		    int xcoord = x - radius + i;
		    if(xcoord < 0)
			xcoord = 0;
		    else if(xcoord >= width)
			xcoord = width-1;

		    int ycoord = y - radius + j;
		    if(ycoord < 0)
			ycoord = 0;
		    else if(ycoord >= height)
			ycoord = height-1;

		    red += gfilter[i][j] * this.pixels[ycoord*width + xcoord].R;
		    green += gfilter[i][j] * this.pixels[ycoord*width + xcoord].G;
		    blue += gfilter[i][j] * this.pixels[ycoord*width + xcoord].B;

		    
		}
	    }
	    Long redl = Math.round(red);
	    Long greenl = Math.round(green);
	    Long bluel = Math.round(blue);
	    return new RGB(redl.intValue(),greenl.intValue(),bluel.intValue());
	    } //end lambda
	    ).collect(Collectors.toList());
	blurred = rgblist.toArray(blurred);
	return new PPMImage(this.width, this.height, this.maxColorVal, blurred);
	
	//throw new ImplementMe();
    }
}
