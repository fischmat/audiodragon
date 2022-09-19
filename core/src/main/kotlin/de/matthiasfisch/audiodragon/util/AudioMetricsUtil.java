package de.matthiasfisch.audiodragon.util;


import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AudioMetricsUtil {

    /**
     * Calculate the Root-Mean Square of the audio data
     *
     * @param audioData the audio data
     * @param format the audio format
     * @return the corresponding RMS value
     */
    public static double getRMS(byte[] audioData, AudioFormat format) {
        long[] frameValues = convertByteArray(audioData, format);
        final double maxPossibleValue = Math.pow(2, format.getSampleSizeInBits() - 1) - 1;
        final double[] doubles = Arrays.stream(frameValues)
                .boxed()
                .mapToDouble(v -> v / maxPossibleValue)
                .toArray();

        double sumOfSquares = Arrays.stream(doubles)
                .map(i -> i * i)
                .sum();
        return Math.sqrt(sumOfSquares / doubles.length);
    }

    public static double[] getFrequencies(byte[] audioData, AudioFormat format) {
        long[] frameValues = convertByteArray(audioData, format);
        final double maxPossibleValue = Math.pow(2, format.getSampleSizeInBits() - 1) - 1;
        final float[] floats = new float[frameValues.length];
        for (int i = 0; i < frameValues.length; i++) {
            floats[i] = (float) (frameValues[i] / maxPossibleValue);
        }
        final int numberOfSamples = audioData.length / format.getFrameSize();
        final JavaFFT fft = new JavaFFT(numberOfSamples);
        final float[][] transformed = fft.transform(floats);
        final float[] realPart = transformed[0];
        final float[] imaginaryPart = transformed[1];
        return toMagnitudes(realPart, imaginaryPart);
    }

    private static double[] toMagnitudes(final float[] realPart, final float[] imaginaryPart) {
        final double[] powers = new double[realPart.length / 2];
        for (int i = 0; i < powers.length; i++) {
            powers[i] = Math.sqrt(realPart[i] * realPart[i] + imaginaryPart[i] * imaginaryPart[i]);
        }
        return powers;
    }

    private static long[] convertByteArray(byte[] audioData, AudioFormat format) {
        final ByteOrder byteOrder = format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN;
        final ByteBuffer buffer = ByteBuffer.wrap(audioData, 0, audioData.length)
                .order(byteOrder);

        final long[] averages = new long[audioData.length / format.getFrameSize()];
        final int framesInSample = audioData.length / format.getFrameSize();
        for (int frameNum = 0; frameNum < framesInSample; frameNum++) {
            final long[] frame = readFrame(buffer, format);
            averages[frameNum / format.getChannels()] = (long) Arrays.stream(frame).average().orElse(0);
        }
        return averages;
    }

    private static long[] readFrame(final ByteBuffer buffer, final AudioFormat format) {
        long[] values = tryReadFrameWithPrimitiveSize(buffer, format);
        if (values != null) {
            return values;
        }
        values = new long[format.getChannels()];
        for (int channel = 0; channel < format.getChannels(); channel++) {
            final int bytesPerChannel = format.getSampleSizeInBits() / 8;
            long value = getByteIndexStream(format.isBigEndian(), bytesPerChannel)
                    .map(i -> ((long) buffer.get()) << (8 * (bytesPerChannel - i - 1)))
                    .reduce((i1, i2) -> i1 | i2)
                    .orElse(0L);
            values[channel] = value;
        }
        return values;
    }

    private static long[] tryReadFrameWithPrimitiveSize(final ByteBuffer buffer, final AudioFormat format) {
        final Supplier<Long> sampleReader;
        switch (format.getSampleSizeInBits()) {
            case 8:
                sampleReader = () -> Byte.valueOf(buffer.get()).longValue();
                break;
            case 16:
                sampleReader = () -> Short.valueOf(buffer.getShort()).longValue();
                break;
            case 32:
                sampleReader = () -> Integer.valueOf(buffer.getInt()).longValue();
                break;
            case 64:
                sampleReader = buffer::getLong;
                break;
            default:
                return null;
        }
        final long[] frame = new long[format.getChannels()];
        for (int channelNum = 0; channelNum < format.getChannels(); channelNum++) {
            frame[channelNum] = sampleReader.get();
        }
        return frame;
    }

    private static Stream<Integer> getByteIndexStream(final boolean isBigEndian, final int limit) {
        final Stream<Integer> stream = IntStream.range(0, limit).boxed();
        if (!isBigEndian) {
            return stream.sorted(Comparator.reverseOrder());
        }
        return stream;
    }
}
