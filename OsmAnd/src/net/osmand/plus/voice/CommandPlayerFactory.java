package net.osmand.plus.voice;

import java.io.File;

import net.osmand.plus.OsmandSettings;
import net.osmand.plus.R;
import net.osmand.plus.ResourceManager;
import net.osmand.plus.activities.OsmandApplication;
import android.content.Context;

public class CommandPlayerFactory {

	public static CommandPlayer createCommandPlayer(String voiceProvider, OsmandApplication osmandApplication, Context ctx)
		throws CommandPlayerException
	{
		if (voiceProvider != null){
			File parent = OsmandSettings.extendOsmandPath(ctx, ResourceManager.VOICE_PATH);
			File voiceDir = new File(parent, voiceProvider);
			if(!voiceDir.exists()){
				throw new CommandPlayerException(ctx.getString(R.string.voice_data_unavailable));
			}
			if (MediaCommandPlayerImpl.isMyData(voiceDir)) {
				return new MediaCommandPlayerImpl(osmandApplication, voiceProvider);
			} else if (TTSCommandPlayerImpl.isMyData(voiceDir)) {
				return new TTSCommandPlayerImpl(osmandApplication, voiceProvider);
			}
			throw new CommandPlayerException(ctx.getString(R.string.voice_data_not_supported));
		}
		return null;
	}
}
